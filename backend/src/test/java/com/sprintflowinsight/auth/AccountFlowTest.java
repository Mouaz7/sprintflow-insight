package com.sprintflowinsight.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.support.HttpFlowTest;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Registration, email changes and account deletion. */
class AccountFlowTest extends HttpFlowTest {
  @Test
  void emailChangeMovesReferencesAndKeepsOtherSessionsBoundToTheSameAccount() {
    registerTestUser();
    String oldEmail = testEmail;
    String newEmail = "changed-" + java.util.UUID.randomUUID() + "@example.com";
    long accountId = userRepository.findByEmail(oldEmail).orElseThrow().getId();
    long projectId = populatedProject("Email migration " + java.util.UUID.randomUUID());
    long taskId = taskRepository.findByProjectId(projectId).get(0).getId();
    jdbc.update("update task set assignee_email = ? where id = ?", oldEmail, taskId);
    long previousTaskVersion = taskRepository.findById(taskId).orElseThrow().getVersion();
    jdbc.update(
        "insert into retrospective_entry (project_id, sprint_id, author_email, category, message,"
            + " created_at, version) values (?, ?, ?, 'good', 'Keep this', ?, 0)",
        projectId,
        taskRepository.findById(taskId).orElseThrow().getSprintId(),
        oldEmail,
        java.sql.Timestamp.from(Instant.now()));
    assertThat(
            request(HttpMethod.POST, "/api/projects/" + projectId + "/reports", Map.of())
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    String originalCookie = sessionCookie;
    sessionCookie = null;
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/auth/login",
                    Map.of("email", oldEmail, "password", "secure-password"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    String otherSession = sessionCookie;
    sessionCookie = originalCookie;
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/auth/profile",
                    Map.of(
                        "currentEmail", oldEmail, "email", newEmail, "displayName", "Changed user"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    assertThat(projectRepository.findById(projectId).orElseThrow().getOwnerEmail())
        .isEqualTo(newEmail);
    assertThat(taskRepository.findById(taskId).orElseThrow().getVersion())
        .isEqualTo(previousTaskVersion + 1);
    for (String reference :
        java.util.List.of(
            "project_member.email",
            "task.assignee_email",
            "task_comment.author_email",
            "notification.recipient_email",
            "activity_event.actor_email",
            "task_status_history.actor_email",
            "retrospective_entry.author_email",
            "shared_report.created_by")) {
      String[] parts = reference.split("\\.");
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from " + parts[0] + " where lower(" + parts[1] + ") = ?",
                  Long.class,
                  oldEmail))
          .as(reference + " no longer belongs to the old email")
          .isZero();
    }
    assertThat(
            rest.exchange(
                    "/api/projects/" + projectId + "/members",
                    HttpMethod.GET,
                    new HttpEntity<>(cookieHeaders()),
                    Object[].class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(registerUser(oldEmail).getStatusCode()).isEqualTo(HttpStatus.OK);
    sessionCookie = otherSession;
    ResponseEntity<Map> me = request(HttpMethod.GET, "/api/auth/me", null);
    assertThat(me.getBody()).containsEntry("email", newEmail);
    assertThat(((Number) me.getBody().get("id")).longValue()).isEqualTo(accountId);
    assertThat(request(HttpMethod.GET, "/api/projects/" + projectId + "/role", null).getBody())
        .containsEntry("role", "Owner");
    String previous = sessionCookie;
    sessionCookie = null;
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/auth/login",
                    Map.of("email", oldEmail, "password", "secure-password"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            request(HttpMethod.GET, "/api/projects/" + projectId + "/role", null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    sessionCookie = previous;
  }

  @Test
  void emailReferenceMigrationRollsBackWithTheProfileTransaction() {
    registerTestUser();
    long projectId =
        id(
            request(
                HttpMethod.POST,
                "/api/projects",
                Map.of("name", "Email rollback " + java.util.UUID.randomUUID())));
    String oldEmail = testEmail;
    var template = new org.springframework.transaction.support.TransactionTemplate(transactions);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                template.executeWithoutResult(
                    status -> {
                      userEmailService.moveReferences(
                          oldEmail, "rollback-" + java.util.UUID.randomUUID() + "@example.com");
                      throw new IllegalStateException("Roll back profile update");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(projectRepository.findById(projectId).orElseThrow().getOwnerEmail())
        .isEqualTo(oldEmail);
    assertThat(
            jdbc.queryForObject(
                "select email from project_member where project_id = ?", String.class, projectId))
        .isEqualTo(oldEmail);
  }

  @Test
  void simultaneousEmailChangesCannotSeparateTheAccountFromItsMembership() throws Exception {
    registerTestUser();
    String oldEmail = testEmail;
    long accountId = userRepository.findByEmail(oldEmail).orElseThrow().getId();
    long projectId =
        id(
            request(
                HttpMethod.POST,
                "/api/projects",
                Map.of("name", "Concurrent email " + java.util.UUID.randomUUID())));
    HttpHeaders firstHeaders = csrfHeaders();
    firstHeaders.setContentType(MediaType.APPLICATION_JSON);
    sessionCookie = null;
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/auth/login",
                    Map.of("email", oldEmail, "password", "secure-password"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    HttpHeaders secondHeaders = csrfHeaders();
    secondHeaders.setContentType(MediaType.APPLICATION_JSON);
    var start = new java.util.concurrent.CountDownLatch(1);
    java.util.function.Function<HttpHeaders, java.util.concurrent.CompletableFuture<HttpStatus>>
        change =
            headers ->
                java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> {
                      try {
                        start.await();
                      } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(error);
                      }
                      var body =
                          Map.of(
                              "currentEmail",
                              oldEmail,
                              "email",
                              "parallel-" + java.util.UUID.randomUUID() + "@example.com",
                              "displayName",
                              "Parallel user");
                      return HttpStatus.valueOf(
                          rest.exchange(
                                  "/api/auth/profile",
                                  HttpMethod.PUT,
                                  new HttpEntity<>(body, headers),
                                  Map.class)
                              .getStatusCode()
                              .value());
                    });
    var first = change.apply(firstHeaders);
    var second = change.apply(secondHeaders);
    start.countDown();
    assertThat(
            java.util.List.of(
                first.get(15, java.util.concurrent.TimeUnit.SECONDS),
                second.get(15, java.util.concurrent.TimeUnit.SECONDS)))
        .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.FORBIDDEN);
    String currentEmail = userRepository.findById(accountId).orElseThrow().getEmail();
    assertThat(projectRepository.findById(projectId).orElseThrow().getOwnerEmail())
        .isEqualTo(currentEmail);
    assertThat(
            jdbc.queryForObject(
                "select email from project_member where project_id = ?", String.class, projectId))
        .isEqualTo(currentEmail);
  }

  @Test
  void registrationHashesPasswordsAndRejectsInvalidData() {
    ResponseEntity<Map> registration =
        request(
            HttpMethod.POST,
            "/api/auth/register",
            Map.of(
                "email",
                "account@example.com",
                "password",
                "secure-password",
                "displayName",
                "Account User"));
    assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(registration.getBody()).doesNotContainKey("password");
    assertThat(userRepository.findByEmail("account@example.com").orElseThrow().getPassword())
        .startsWith("$2");

    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/auth/login",
                    Map.of("email", "account@example.com", "password", "secure-password"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/auth/password/change",
                    Map.of(
                        "email",
                        "account@example.com",
                        "currentPassword",
                        "secure-password",
                        "newPassword",
                        "new-secure-password"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    String changedHash =
        userRepository.findByEmail("account@example.com").orElseThrow().getPassword();
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    assertThat(encoder.matches("secure-password", changedHash)).isFalse();
    assertThat(encoder.matches("new-secure-password", changedHash)).isTrue();
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/auth/login",
                    Map.of("email", "account@example.com", "password", "new-secure-password"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    ResponseEntity<Map> profile =
        request(
            HttpMethod.PUT,
            "/api/auth/profile",
            Map.of(
                "currentEmail",
                "account@example.com",
                "email",
                "alex@example.com",
                "displayName",
                "Alex Example",
                "jobTitle",
                "Product owner",
                "bio",
                "Plans product work.",
                "avatarImage",
                ""));
    assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(profile.getBody()).containsEntry("displayName", "Alex Example");
    assertThat(profile.getBody()).containsEntry("email", "alex@example.com");
    assertThat(userRepository.findByEmail("alex@example.com")).isPresent();
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/tasks",
                    Map.of("title", "", "description", "", "status", "ToDo"))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void accountDeletionRequiresOwnershipTransferAndCleansAccountReferences() {
    registerTestUser();
    String ownerCookie = sessionCookie;
    String ownerEmail = testEmail;
    ResponseEntity<Map> project =
        request(
            HttpMethod.POST,
            "/api/projects",
            Map.of(
                "name",
                "Account lifecycle",
                "description",
                "",
                "ownerEmail",
                "ignored@example.com"));
    long projectId = id(project);
    ResponseEntity<Map> task =
        request(
            HttpMethod.POST,
            "/api/tasks",
            Map.of(
                "title",
                "Assigned owner task",
                "status",
                "To Do",
                "projectId",
                projectId,
                "assigneeEmail",
                ownerEmail));
    long taskId = id(task);
    assertThat(
            taskHistoryRepository.findByTaskIdOrderByChangedAtDesc(taskId).stream()
                .map(event -> event.getActorEmail())
                .toList())
        .contains(ownerEmail);
    assertThat(
            request(
                    HttpMethod.DELETE,
                    "/api/auth/account",
                    Map.of("email", ownerEmail, "currentPassword", "secure-password"))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);

    String successorEmail = "successor-" + java.util.UUID.randomUUID() + "@example.com";
    ResponseEntity<Map> registeredSuccessor = registerUser(successorEmail);
    assertThat(registeredSuccessor.getStatusCode()).isEqualTo(HttpStatus.OK);
    sessionCookie = ownerCookie;
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/projects/" + projectId + "/members",
                    Map.of("email", successorEmail, "role", "Member"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/projects/" + projectId + "/owner",
                    Map.of("email", successorEmail))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            request(
                    HttpMethod.DELETE,
                    "/api/auth/account",
                    Map.of("email", ownerEmail, "currentPassword", "secure-password"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(userRepository.findByEmail(ownerEmail)).isEmpty();
    assertThat(projectRepository.findById(projectId).orElseThrow().getOwnerEmail())
        .isEqualTo(successorEmail);
    assertThat(taskRepository.findById(taskId).orElseThrow().getAssigneeEmail()).isNull();
    assertThat(
            taskHistoryRepository.findByTaskIdOrderByChangedAtDesc(taskId).stream()
                .map(event -> event.getActorEmail())
                .toList())
        .doesNotContain(ownerEmail)
        .contains("Former member");
    assertThat(
            rest.exchange(
                    "/api/auth/me", HttpMethod.GET, new HttpEntity<>(cookieHeaders()), Map.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
