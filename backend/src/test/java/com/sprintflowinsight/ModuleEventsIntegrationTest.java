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
    return projects.save(project);
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
                "member@example.com",
                Instant.now(),
                42L,
                43L,
                "A task",
                project.getOwnerEmail()))
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
                                  "member@example.com",
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
        .notifyProjectOwner(eq(project.getId()), anyString(), eq("Retry me"), eq(id));
    scenario
        .publish(
            new NotificationRequested(
                id, project.getId(), "member@example.com", Instant.now(), null, "Retry me", true))
        .andWaitForStateChange(() -> pending(id), count -> count == 1)
        .andVerify(count -> assertThat(delivered(id)).isZero());
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                verify(delivery)
                    .notifyProjectOwner(project.getId(), "member@example.com", "Retry me", id));
    // Ensure the failing attempt has returned before retrying it.
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(pending(id)).isEqualTo(1));
    doCallRealMethod()
        .when(delivery)
        .notifyProjectOwner(eq(project.getId()), anyString(), eq("Retry me"), eq(id));
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
                id, project.getId(), "member@example.com", Instant.now(), null, "Retry me", true))
        .andWaitForStateChange(() -> pending(id), count -> count == 0)
        .andVerify(count -> assertThat(delivered(id)).isEqualTo(1));
  }
}
