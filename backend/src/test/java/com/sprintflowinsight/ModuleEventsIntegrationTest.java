package com.sprintflowinsight;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.auth.UserRepository;
import com.sprintflowinsight.notifications.NotificationRepository;
import com.sprintflowinsight.notifications.NotificationService;
import com.sprintflowinsight.support.PostgresTestContainer;
import com.sprintflowinsight.workspace.events.*;
import com.sprintflowinsight.workspace.project.Project;
import com.sprintflowinsight.workspace.project.ProjectRepository;
import com.sprintflowinsight.workspace.task.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real JDBC outbox, actual asynchronous listeners, commits, rollbacks and replay on PostgreSQL. */
@SpringBootTest(
    properties = {
      "app.security.auth-rate-limit.enabled=false",
      "app.events.retry-delay-ms=3600000"
    })
@Import(PostgresTestContainer.class)
@EnableScenarios
class ModuleEventsIntegrationTest {
  @Autowired UserRepository users;
  @Autowired ProjectRepository projects;
  @Autowired TaskRepository tasks;
  @Autowired TaskService taskService;
  @Autowired NotificationRepository notifications;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  @Autowired ApplicationEventPublisher events;
  @Autowired IncompleteEventPublications publications;
  @MockitoSpyBean NotificationService delivery;

  private Project project() {
    User user = new User();
    user.setEmail("owner-" + UUID.randomUUID() + "@example.com");
    user.setDisplayName("Owner");
    user.setPassword("not-used-in-this-test");
    users.save(user);
    Project project = new Project();
    project.setName("Events " + UUID.randomUUID());
    project.setOwnerEmail(user.getEmail());
    project.setKey(com.sprintflowinsight.support.ApiIntegrationTest.uniqueKeyForTests());
    return projects.save(project);
  }

  private Long ownerId(Project project) {
    return users.findByEmail(project.getOwnerEmail()).orElseThrow().getId();
  }

  private long pending(UUID id) {
    return jdbc.queryForObject(
        "select count(*) from event_publication where serialized_event like ?",
        Long.class,
        "%" + id + "%");
  }

  private long delivered(UUID id) {
    return jdbc.queryForObject(
        "select count(*) from notification where source_event_id=?", Long.class, id);
  }

  @Test
  void committedTaskMovementPublishesTypedEventAndNotifiesAfterCommit(Scenario scenario) {
    Project project = project();
    Task task = new Task("Move me", "", "To Do");
    task.setProjectId(project.getId());
    task = taskService.createTask(task, project.getOwnerEmail());
    Task original = task;
    Task changes = new Task("Move me", "", "Done");
    changes.setProjectId(project.getId());
    scenario
        .stimulate(() -> taskService.updateTask(original, changes, "member@example.com"))
        .andWaitForEventOfType(TaskMoved.class)
        .matching(event -> event.taskId().equals(original.getId()))
        .toArriveAndVerify(
            event -> {
              assertThat(event.previousStatus()).isEqualTo("To Do");
              assertThat(event.status()).isEqualTo("Done");
              await()
                  .atMost(Duration.ofSeconds(10))
                  .untilAsserted(
                      () -> {
                        assertThat(delivered(event.eventId())).isEqualTo(1);
                        assertThat(pending(event.eventId())).isZero();
                      });
            });
  }

  @Test
  void commentsDeliverOneNotificationWhenOwnerIsAlsoAssignee(Scenario scenario) {
    Project project = project();
    UUID id = UUID.randomUUID();
    scenario
        .publish(
            new CommentAdded(
                id,
                project.getId(),
                null,
                Instant.now(),
                42L,
                43L,
                "A task",
                ownerId(project)))
        .andWaitForStateChange(() -> delivered(id), count -> count == 1)
        .andVerify(count -> assertThat(count).isEqualTo(1));
    await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(pending(id)).isZero());
  }

  @Test
  void rollbackRemovesBothMutationAndPublication() {
    Project project = project();
    UUID id = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                new TransactionTemplate(manager)
                    .executeWithoutResult(
                        tx -> {
                          Project managed = projects.findById(project.getId()).orElseThrow();
                          managed.setDescription("must roll back");
                          projects.save(managed);
                          events.publishEvent(
                              new NotificationRequested(
                                  id,
                                  project.getId(),
                                  null,
                                  Instant.now(),
                                  null,
                                  "Rolled back",
                                  true));
                          assertThat(pending(id))
                              .isEqualTo(1); // registry participates in this transaction
                          assertThat(delivered(id)).isZero(); // listener cannot run before commit
                          throw new IllegalStateException("roll back");
                        }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(projects.findById(project.getId()).orElseThrow().getDescription()).isNull();
    assertThat(pending(id)).isZero();
    assertThat(delivered(id)).isZero();
  }

  @Test
  void failedDeliverySurvivesInRegistryAndReplayIsIdempotent(Scenario scenario) {
    Project project = project();
    UUID id = UUID.randomUUID();
    doThrow(new IllegalStateException("simulated listener outage"))
        .when(delivery)
        .notifyProjectOwner(eq(project.getId()), isNull(), eq("Retry me"), eq(id));
    scenario
        .publish(
            new NotificationRequested(
                id, project.getId(), null, Instant.now(), null, "Retry me", true))
        .andWaitForStateChange(() -> pending(id), count -> count == 1)
        .andVerify(count -> assertThat(delivered(id)).isZero());
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                verify(delivery)
                    .notifyProjectOwner(project.getId(), null, "Retry me", id));
    // Ensure the failing attempt has returned before retrying it.
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(pending(id)).isEqualTo(1));
    doCallRealMethod()
        .when(delivery)
        .notifyProjectOwner(eq(project.getId()), isNull(), eq("Retry me"), eq(id));
    publications.resubmitIncompletePublicationsOlderThan(Duration.ZERO);
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(delivered(id)).isEqualTo(1);
              assertThat(pending(id)).isZero();
            });
    // Simulate a crash after the consumer committed but before registry completion: same event
    // arrives again.
    scenario
        .publish(
            new NotificationRequested(
                id, project.getId(), null, Instant.now(), null, "Retry me", true))
        .andWaitForStateChange(() -> pending(id), count -> count == 0)
        .andVerify(count -> assertThat(delivered(id)).isEqualTo(1));
  }

  @Test
  void replaysAnExistingA3PublicationWithJackson3() {
    Project project = project();
    UUID eventId = UUID.randomUUID();
    String listenerId =
        new TransactionTemplate(manager)
            .execute(
                tx -> {
                  events.publishEvent(
                      new NotificationRequested(
                          eventId,
                          project.getId(),
                          null,
                          Instant.now(),
                          null,
                          "Pre-upgrade publication",
                          true));
                  String target =
                      jdbc.queryForObject(
                          "select listener_id from event_publication where serialized_event like ?",
                          String.class,
                          "%" + eventId + "%");
                  tx.setRollbackOnly();
                  return target;
                });
    // Literal Jackson 2 record JSON as written by A3, persisted before the runtime upgrade and
    // before events referenced users by id. The email fields are ignored on replay and the
    // project-owner notification is still delivered.
    String previousJson =
        """
{"eventId":"%s","projectId":%d,"actorEmail":"member@example.com","occurredAt":"2026-01-01T10:00:00Z","recipientEmail":null,"message":"Pre-upgrade publication","projectOwner":true}
"""
            .formatted(eventId, project.getId());
    jdbc.update(
        "insert into event_publication(id,listener_id,event_type,serialized_event,publication_date)"
            + " values (?,?,?,?,?)",
        UUID.randomUUID(),
        listenerId,
        NotificationRequested.class.getName(),
        previousJson,
        java.sql.Timestamp.from(Instant.now().minusSeconds(3600)));
    publications.resubmitIncompletePublicationsOlderThan(Duration.ZERO);
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(delivered(eventId)).isEqualTo(1);
              assertThat(pending(eventId)).isZero();
            });
  }

  @Test
  void recipientIsResolvedAtDeliverySoAnEmailChangeBeforeDeliveryStillArrives() {
    Project project = project();
    User member = new User();
    member.setEmail("member-" + UUID.randomUUID() + "@example.com");
    member.setDisplayName("Member");
    member.setPassword("not-used-in-this-test");
    users.save(member);
    UUID id = UUID.randomUUID();
    String changedEmail = "renamed-" + UUID.randomUUID() + "@example.com";
    new TransactionTemplate(manager)
        .executeWithoutResult(
            tx -> {
              events.publishEvent(
                  new NotificationRequested(
                      id, project.getId(), null, Instant.now(), member.getId(), "Hello", false));
              User managed = users.findById(member.getId()).orElseThrow();
              managed.setEmail(changedEmail);
              users.save(managed);
            });
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(delivered(id)).isEqualTo(1);
              assertThat(pending(id)).isZero();
            });
    assertThat(
            jdbc.queryForObject(
                "select recipient_email from notification where source_event_id=?",
                String.class,
                id))
        .isEqualTo(changedEmail);
  }

  @Test
  void deletingAnAccountRemovesOnlyItsUndeliverableRequests() {
    Project project = project();
    String listenerId =
        new TransactionTemplate(manager)
            .execute(
                tx -> {
                  UUID probe = UUID.randomUUID();
                  events.publishEvent(
                      new NotificationRequested(
                          probe, project.getId(), null, Instant.now(), 1L, "probe", false));
                  String target =
                      jdbc.queryForObject(
                          "select listener_id from event_publication where serialized_event like ?",
                          String.class,
                          "%" + probe + "%");
                  tx.setRollbackOnly();
                  return target;
                });
    UUID forDeleted = UUID.randomUUID(), forOther = UUID.randomUUID();
    insertPending(listenerId, forDeleted, project.getId(), 900_000_001L);
    insertPending(listenerId, forOther, project.getId(), 9_000_000_011L);
    new TransactionTemplate(manager)
        .executeWithoutResult(
            tx ->
                events.publishEvent(
                    new com.sprintflowinsight.auth.AccountDeleting(
                        900_000_001L, "gone-" + UUID.randomUUID() + "@example.com")));
    assertThat(pending(forDeleted)).isZero();
    assertThat(pending(forOther))
        .as("an id that merely starts with the deleted id is another user")
        .isEqualTo(1);
    jdbc.update("delete from event_publication where serialized_event like ?", "%" + forOther + "%");
  }

  private void insertPending(String listenerId, UUID eventId, Long projectId, Long recipient) {
    jdbc.update(
        "insert into event_publication(id,listener_id,event_type,serialized_event,publication_date)"
            + " values (?,?,?,?,?)",
        UUID.randomUUID(),
        listenerId,
        NotificationRequested.class.getName(),
        """
{"eventId":"%s","projectId":%d,"actorUserId":null,"occurredAt":"2026-01-01T10:00:00Z","recipientUserId":%d,"message":"Pending","projectOwner":false}
"""
            .strip()
            .formatted(eventId, projectId, recipient),
        java.sql.Timestamp.from(Instant.now()));
  }
}
