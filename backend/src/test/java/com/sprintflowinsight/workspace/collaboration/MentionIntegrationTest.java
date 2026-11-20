package com.sprintflowinsight.workspace.collaboration;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.sprintflowinsight.notifications.*;
import com.sprintflowinsight.support.ApiIntegrationTest;
import com.sprintflowinsight.workspace.task.*;
import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class MentionIntegrationTest extends ApiIntegrationTest {
  @Autowired TaskRepository tasks;
  @Autowired CommentMentions mentions;
  @Autowired NotificationRepository notifications;
  @Autowired NotificationService delivery;
  @Autowired JdbcTemplate jdbc;
  static final AtomicInteger NUMBERS = new AtomicInteger();

  private Task task(Long projectId) {
    var task = new Task("Mention subject", null, "To Do");
    task.setProjectId(projectId);
    task.setNumber(NUMBERS.incrementAndGet());
    return tasks.saveAndFlush(task);
  }

  @Test
  void resolvesMembersPersistsMentionsAndNotifiesOnlyNewRecipientsWithDurableDedupe()
      throws Exception {
    var owner = user();
    var recipient = user();
    var outsider = user();
    var p = project(owner, recipient);
    var task = task(p.getId());
    String path = "/api/tasks/" + task.getId() + "/comments";
    String text =
        "Hi @"
            + recipient.getEmail()
            + " @"
            + recipient.getEmail()
            + " @"
            + outsider.getEmail()
            + " @"
            + owner.getEmail();
    var created =
        mvc.perform(
                post(path)
                    .with(as(owner))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"message\":\"" + text + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    long commentId =
        ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
    assertThat(mentions.userIds(commentId))
        .containsExactlyInAnyOrder(owner.getId(), recipient.getId());
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(
                        notifications.findByRecipientEmailOrderByCreatedAtDesc(
                            recipient.getEmail()))
                    .anyMatch(n -> n.getMessage().startsWith("Mentioned you")));
    assertThat(notifications.findByRecipientEmailOrderByCreatedAtDesc(outsider.getEmail()))
        .isEmpty();
    var notification =
        notifications.findByRecipientEmailOrderByCreatedAtDesc(recipient.getEmail()).stream()
            .filter(n -> n.getMessage().startsWith("Mentioned you"))
            .findFirst()
            .orElseThrow();
    delivery.notifyMention(
        p.getId(),
        recipient.getId(),
        owner.getId(),
        notification.getMessage(),
        notification.getSourceEventId());
    assertThat(
            notifications.findByRecipientEmailOrderByCreatedAtDesc(recipient.getEmail()).stream()
                .filter(n -> n.getMessage().startsWith("Mentioned you")))
        .hasSize(1);
    // Same text edits update no recipients and do not publish another mention.
    mvc.perform(
            put(path + "/" + commentId)
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + text + "\",\"version\":0}"))
        .andExpect(status().isOk());
    assertThat(mentions.userIds(commentId))
        .containsExactlyInAnyOrder(owner.getId(), recipient.getId());
    mvc.perform(
            put(path + "/" + commentId)
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Removed mentions\",\"version\":0}"))
        .andExpect(status().isOk());
    assertThat(mentions.userIds(commentId)).isEmpty();
    mvc.perform(delete(path + "/" + commentId).with(as(owner)).with(csrf()))
        .andExpect(status().isNoContent());
  }

  @ParameterizedTest
  @ValueSource(strings = {"Owner", "Admin", "Member", "Viewer", "Outsider", "Archived", "Trashed"})
  void commentMentionsRespectEveryProjectBoundary(String role) throws Exception {
    var owner = user();
    var caller = List.of("Owner", "Archived", "Trashed").contains(role) ? owner : user();
    var p = project(owner, null);
    var task = task(p.getId());
    if (List.of("Admin", "Member", "Viewer").contains(role)) member(p, caller, role);
    if (role.equals("Archived")) p.setArchivedAt(Instant.now());
    if (role.equals("Trashed")) p.setTrashedAt(Instant.now());
    projects.save(p);
    int expected =
        role.equals("Archived")
            ? 409
            : role.equals("Trashed")
                ? 404
                : List.of("Viewer", "Outsider").contains(role) ? 403 : 200;
    mvc.perform(
            post("/api/tasks/" + task.getId() + "/comments")
                .with(as(caller))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Hello @" + owner.getEmail() + "\"}"))
        .andExpect(status().is(expected));
  }

  @Test
  void crossProjectRelationsAndLateDeliveryToRemovedMembersAreRejected() {
    var owner = user();
    var recipient = user();
    var p = project(owner, recipient);
    var other = project(owner, null);
    var task = task(p.getId());
    var comment = new TaskComment();
    comment.setTaskId(task.getId());
    comment.setProjectId(p.getId());
    comment.setAuthorEmail(owner.getEmail());
    comment.setMessage("Test");
    comment.setCreatedAt(Instant.now());
    Long id =
        jdbc.queryForObject(
            "insert into task_comment(task_id,project_id,author_email,message,created_at,version)"
                + " values(?,?,?,?,?,0) returning id",
            Long.class,
            task.getId(),
            p.getId(),
            owner.getEmail(),
            "Test",
            java.sql.Timestamp.from(Instant.now()));
    assertThatThrownBy(
            () -> mentions.replace(id, other.getId(), List.of(recipient.getId()), Instant.now()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    jdbc.update(
        "delete from project_member where project_id=? and user_id=?",
        p.getId(),
        recipient.getId());
    delivery.notifyMention(
        p.getId(), recipient.getId(), owner.getId(), "PRIVATE", java.util.UUID.randomUUID());
    assertThat(notifications.findByRecipientEmailOrderByCreatedAtDesc(recipient.getEmail()))
        .isEmpty();
  }
}
