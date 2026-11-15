package com.sprintflowinsight.project;


import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.support.HttpFlowTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Project membership, isolation, versions, pagination, roles and deletion. */
class ProjectFlowTest extends HttpFlowTest {
  @Test
  void removingMemberUnassignsOnlyTheirProjectTasksAndRecordsTheActor() {
    registerTestUser();
    String owner = testEmail;
    String member = "removed-" + java.util.UUID.randomUUID() + "@example.com";
    registerUser(member);
    long first =
        id(
            request(
                HttpMethod.POST,
                "/api/projects",
                Map.of("name", "Members A " + java.util.UUID.randomUUID())));
    long second =
        id(
            request(
                HttpMethod.POST,
                "/api/projects",
                Map.of("name", "Members B " + java.util.UUID.randomUUID())));
    for (long projectId : new long[] {first, second}) {
      assertThat(
              request(
                      HttpMethod.POST,
                      "/api/projects/" + projectId + "/members",
                      Map.of("email", member, "role", "Member"))
                  .getStatusCode())
          .isEqualTo(HttpStatus.OK);
      assertThat(
              request(
                      HttpMethod.POST,
                      "/api/tasks",
                      Map.of(
                          "title",
                          "Assigned",
                          "status",
                          "To Do",
                          "projectId",
                          projectId,
                          "assigneeEmail",
                          member))
                  .getStatusCode())
          .isEqualTo(HttpStatus.OK);
    }
    var before = taskRepository.findByProjectId(first).get(0);
    assertThat(
            request(HttpMethod.DELETE, "/api/projects/" + first + "/members/" + member, null)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    var after = taskRepository.findById(before.getId()).orElseThrow();
    assertThat(after.getAssigneeEmail()).isNull();
    assertThat(after.getVersion()).isGreaterThan(before.getVersion());
    assertThat(taskRepository.findByProjectId(second).get(0).getAssigneeEmail()).isEqualTo(member);
    var events =
        activityEventRepository.findByProjectIdOrderByCreatedAtDesc(first).stream()
            .filter(event -> "MEMBER_REMOVED".equals(event.getEventType()))
            .toList();
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getActorEmail()).isEqualTo(owner);
    assertThat(events.get(0).getActorUserId()).isNotNull();
    assertThat(events.get(0).getCreatedAt()).isNotNull();
  }

  @Test
  void invalidWorkflowAndMemberRequestsAreRejectedWithoutServerErrors() {
    registerTestUser();
    long projectId = populatedProject("Input guards " + java.util.UUID.randomUUID());
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/tasks",
                    Map.of("title", "Missing status", "projectId", projectId))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/tasks",
                    Map.of(
                        "title",
                        "Missing reason",
                        "status",
                        "To Do",
                        "projectId",
                        projectId,
                        "blocked",
                        true))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/projects/" + projectId + "/members",
                    Map.of("role", "Member"))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/projects/" + projectId + "/members",
                    Map.of("email", testEmail, "role", "Viewer"))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            request(HttpMethod.GET, "/api/projects/" + projectId + "/role", null)
                .getBody()
                .get("role"))
        .isEqualTo("Owner");
  }

  @Test
  void projectListsAndTaskAssociationsStayIsolated() {
    registerTestUser();
    long first = populatedProject("Isolation A " + java.util.UUID.randomUUID());
    long second = populatedProject("Isolation B " + java.util.UUID.randomUUID());
    var task = taskRepository.findByProjectId(first).get(0);
    for (long projectId : new long[] {first, second}) {
      for (String path :
          new String[] {
            "/api/tasks?projectId=" + projectId,
            "/api/sprints?projectId=" + projectId,
            "/api/stories/project/" + projectId
          }) {
        var response =
            rest.exchange(
                path, HttpMethod.GET, new HttpEntity<>(cookieHeaders()), java.util.List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        for (Object item : response.getBody()) {
          assertThat(((Number) ((Map<?, ?>) item).get("projectId")).longValue())
              .isEqualTo(projectId);
        }
      }
    }
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/tasks",
                    Map.of(
                        "title",
                        "Wrong sprint",
                        "status",
                        "To Do",
                        "projectId",
                        second,
                        "sprintId",
                        task.getSprintId()))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/tasks",
                    Map.of(
                        "title",
                        "Wrong story",
                        "status",
                        "To Do",
                        "projectId",
                        second,
                        "storyId",
                        task.getStoryId()))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/tasks/" + task.getId(),
                    Map.of("title", "Moved task", "status", "To Do", "projectId", second))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(taskRepository.findById(task.getId()).orElseThrow().getProjectId()).isEqualTo(first);
  }

  @Test
  void projectEditsRequireAnObservedVersion() {
    registerTestUser();
    long id =
        id(
            request(
                HttpMethod.POST,
                "/api/projects",
                Map.of("name", "Versioned project " + java.util.UUID.randomUUID())));
    Map<String, Object> change = Map.of("name", "Updated project " + java.util.UUID.randomUUID());
    assertThat(
            rest.exchange(
                    "/api/projects/" + id,
                    HttpMethod.PUT,
                    new HttpEntity<>(change, csrfHeaders()),
                    Map.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
    var stale = new java.util.HashMap<String, Object>(change);
    stale.put("version", 99999L);
    assertThat(request(HttpMethod.PUT, "/api/projects/" + id, stale).getStatusCode())
        .isEqualTo(HttpStatus.PRECONDITION_FAILED);
    assertThat(request(HttpMethod.PUT, "/api/projects/" + id, change).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void projectCreationCannotOverwriteAnotherAccountsProject() {
    registerTestUser();
    String owner = testEmail;
    long originalId =
        id(
            request(
                HttpMethod.POST,
                "/api/projects",
                Map.of("name", "Protected project " + java.util.UUID.randomUUID())));
    long originalVersion = projectRepository.findById(originalId).orElseThrow().getVersion();
    registerTestUser();
    var created =
        request(
            HttpMethod.POST,
            "/api/projects",
            Map.of(
                "id",
                originalId,
                "version",
                originalVersion,
                "name",
                "Independent project " + java.util.UUID.randomUUID()));
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(id(created)).isNotEqualTo(originalId);
    assertThat(projectRepository.findById(originalId).orElseThrow().getOwnerEmail())
        .isEqualTo(owner);
    assertThat(request(HttpMethod.GET, "/api/projects/" + originalId, null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void projectPaginationFiltersAccessBeforeApplyingThePage() {
    registerTestUser();
    long first =
        id(
            request(
                HttpMethod.POST,
                "/api/projects",
                Map.of("name", "Paged first " + java.util.UUID.randomUUID())));
    long second =
        id(
            request(
                HttpMethod.POST,
                "/api/projects",
                Map.of("name", "Paged second " + java.util.UUID.randomUUID())));
    var page =
        rest.exchange(
            "/api/projects?page=1&size=1",
            HttpMethod.GET,
            new HttpEntity<>(cookieHeaders()),
            Map[].class);
    assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(page.getBody()).hasSize(1);
    assertThat(((Number) page.getBody()[0].get("id")).longValue()).isEqualTo(second);
    assertThat(request(HttpMethod.GET, "/api/projects?page=-1", null).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(request(HttpMethod.GET, "/api/projects?size=201", null).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(first).isLessThan(second);
  }

  @Test
  void deletingPopulatedProjectCleansAllDataAndFilesAndPreservesOtherProjects() throws Exception {
    registerTestUser();
    String ownerCookie = sessionCookie;
    String ownerEmail = testEmail;
    long projectId = populatedProject("Cascade " + java.util.UUID.randomUUID());
    long otherId = populatedProject("Preserved " + java.util.UUID.randomUUID());
    String[] tables = {
      "project_member",
      "user_stories",
      "sprint",
      "task",
      "task_status_history",
      "task_comment",
      "task_attachment",
      "activity_event",
      "notification"
    };
    Map<String, Long> preservedCounts = new java.util.HashMap<>();
    for (String table : tables) {
      assertThat(projectRows(table, projectId)).as(table + " is populated").isPositive();
      preservedCounts.put(table, projectRows(table, otherId));
    }
    String key =
        jdbc.queryForObject(
            "select storage_key from task_attachment where project_id = ? and storage_key is not"
                + " null",
            String.class,
            projectId);
    java.nio.file.Path file = java.nio.file.Path.of("data", "attachments", key).toAbsolutePath();
    assertThat(file).exists();

    String otherEmail = "cascade-member-" + java.util.UUID.randomUUID() + "@example.com";
    ResponseEntity<Map> registered = registerUser(otherEmail);
    String otherCookie = registered.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
    sessionCookie = otherCookie;
    assertThat(request(HttpMethod.DELETE, "/api/projects/" + projectId, null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    for (String role : java.util.List.of("Viewer", "Member", "Admin")) {
      sessionCookie = ownerCookie;
      assertThat(
              request(
                      HttpMethod.POST,
                      "/api/projects/" + projectId + "/members",
                      Map.of("email", otherEmail, "role", role))
                  .getStatusCode())
          .isEqualTo(HttpStatus.OK);
      sessionCookie = otherCookie;
      ResponseEntity<Map> denied = request(HttpMethod.DELETE, "/api/projects/" + projectId, null);
      assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(denied.getBody())
          .containsEntry("message", "Only the project owner can delete this project.");
      assertThat(taskRepository.findByProjectId(projectId)).hasSize(1);
      assertThat(file).exists();
    }

    // A failed transaction restores every record and must leave physical files intact.
    var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
    var owner = userRepository.findByEmail(ownerEmail).orElseThrow();
    var identity = new com.sprintflowinsight.auth.UserIdentity(owner.getId(), owner.getEmail());
    context.setAuthentication(
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            identity, null, java.util.List.of()));
    try {
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () ->
                  new org.springframework.transaction.support.TransactionTemplate(transactions)
                      .executeWithoutResult(
                          status -> {
                            projectDeletion.delete(projectId, null);
                            throw new IllegalStateException("Simulated rollback");
                          }))
          .isInstanceOf(IllegalStateException.class);
    } finally {
      org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
    for (String table : tables)
      assertThat(projectRows(table, projectId)).as(table + " restored after rollback").isPositive();
    assertThat(projectRepository.existsById(projectId)).isTrue();
    assertThat(file).exists();

    sessionCookie = ownerCookie;
    assertThat(request(HttpMethod.DELETE, "/api/projects/" + projectId, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(projectRepository.existsById(projectId)).isFalse();
    for (String table : tables) {
      assertThat(projectRows(table, projectId)).as(table + " cleared").isZero();
      assertThat(projectRows(table, otherId))
          .as(table + " in another project preserved")
          .isEqualTo(preservedCounts.get(table));
    }
    assertThat(file).doesNotExist();
    assertThat(request(HttpMethod.DELETE, "/api/projects/" + projectId, null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(request(HttpMethod.DELETE, "/api/projects/" + otherId, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void viewerRoleBlocksWritesAndProjectDataRequiresLogin() {
    registerTestUser();
    String ownerCookie = sessionCookie;
    ResponseEntity<Map> project =
        request(
            HttpMethod.POST,
            "/api/projects",
            Map.of(
                "name",
                "Role verification",
                "description",
                "",
                "ownerEmail",
                "ignored@example.com"));
    assertThat(project.getStatusCode())
        .as("project response: %s", project.getBody())
        .isEqualTo(HttpStatus.OK);
    long projectId = id(project);
    String viewerEmail = "viewer-" + java.util.UUID.randomUUID() + "@example.com";
    ResponseEntity<Map> viewer = registerUser(viewerEmail);
    String viewerCookie = viewer.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
    sessionCookie = ownerCookie;
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/projects/" + projectId + "/members",
                    Map.of("email", viewerEmail, "role", "Viewer"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    sessionCookie = viewerCookie;
    assertThat(
            rest.exchange(
                    "/api/projects",
                    HttpMethod.GET,
                    new HttpEntity<>(cookieHeaders()),
                    Object[].class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    ResponseEntity<Map> viewerRole =
        rest.exchange(
            "/api/projects/" + projectId + "/role",
            HttpMethod.GET,
            new HttpEntity<>(cookieHeaders()),
            Map.class);
    assertThat(viewerRole.getBody().get("role")).isEqualTo("Viewer");
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/projects/" + projectId,
                    Map.of(
                        "name",
                        "Should be blocked",
                        "description",
                        "",
                        "ownerEmail",
                        "ignored@example.com"))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    sessionCookie = ownerCookie;
    String adminEmail = "admin-" + java.util.UUID.randomUUID() + "@example.com";
    ResponseEntity<Map> adminRegistration = registerUser(adminEmail);
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/projects/" + projectId + "/members",
                    Map.of("email", adminEmail, "role", "Admin"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    sessionCookie =
        adminRegistration.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/projects/" + projectId,
                    Map.of(
                        "name",
                        "Admin updated project",
                        "description",
                        "",
                        "ownerEmail",
                        "ignored@example.com"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/projects/" + projectId + "/owner",
                    Map.of("email", adminEmail))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    sessionCookie = null;
    HttpHeaders noSession = new HttpHeaders();
    assertThat(
            rest.exchange(
                    "/api/projects", HttpMethod.GET, new HttpEntity<>(noSession), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
