package com.sprintflowinsight;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.auth.UserRepository;
import com.sprintflowinsight.project.ProjectRepository;
import com.sprintflowinsight.repository.ActivityEventRepository;
import com.sprintflowinsight.repository.TaskRepository;
import com.sprintflowinsight.repository.TaskStatusHistoryRepository;
import com.sprintflowinsight.service.NotificationService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.security.auth-rate-limit.enabled=false",
      "spring.datasource.url=${SPRINTFLOW_TEST_DB_URL:jdbc:h2:mem:sprintflow-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
      "spring.datasource.driver-class-name=${SPRINTFLOW_TEST_DB_DRIVER:org.h2.Driver}",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.h2.console.enabled=false",
      "spring.flyway.enabled=true"
    })
class SprintFlowIntegrationTest {

  @Autowired private com.sprintflowinsight.story.UserStoryRepository storyRepository;

  @Test
  void manualBacklogOrderIsPersistentVersionedAndProjectScoped() {
    registerTestUser();
    long projectId = populatedProject("Ordering " + java.util.UUID.randomUUID());
    request(
        HttpMethod.POST,
        "/api/stories",
        Map.of(
            "title",
            "Second story",
            "projectId",
            projectId,
            "acceptanceCriteria",
            "Order persists",
            "storyPoints",
            3,
            "priorityId",
            2));
    var current = storyRepository.findByProjectId(projectId);
    var ordered = new java.util.ArrayList<Map<String, Object>>();
    for (int index = current.size() - 1; index >= 0; index--) {
      var story = current.get(index);
      ordered.add(Map.of("id", story.getId(), "version", story.getVersion()));
    }
    String path = "/api/stories/project/" + projectId + "/order";
    assertThat(
            rest.exchange(
                    path,
                    HttpMethod.PUT,
                    new HttpEntity<>(Map.of("items", ordered), csrfHeaders()),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            storyRepository.findByProjectId(projectId).stream().map(item -> item.getId()).toList())
        .containsExactly(current.get(1).getId(), current.get(0).getId());
    assertThat(
            rest.exchange(
                    path,
                    HttpMethod.PUT,
                    new HttpEntity<>(Map.of("items", ordered), csrfHeaders()),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.PRECONDITION_FAILED);
    assertThat(
            rest.exchange(
                    path,
                    HttpMethod.PUT,
                    new HttpEntity<>(Map.of("items", List.of(ordered.get(0))), csrfHeaders()),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    var missingVersions =
        storyRepository.findByProjectId(projectId).stream()
            .map(item -> Map.of("id", item.getId()))
            .toList();
    assertThat(
            rest.exchange(
                    path,
                    HttpMethod.PUT,
                    new HttpEntity<>(Map.of("items", missingVersions), csrfHeaders()),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
    String viewerEmail = "order-viewer-" + java.util.UUID.randomUUID() + "@example.com";
    var viewer = registerUser(viewerEmail);
    request(
        HttpMethod.POST,
        "/api/projects/" + projectId + "/members",
        Map.of("email", viewerEmail, "role", "Viewer"));
    sessionCookie = viewer.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
    assertThat(
            rest.exchange(
                    path,
                    HttpMethod.PUT,
                    new HttpEntity<>(Map.of("items", ordered), csrfHeaders()),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void boardExportsApplyAllFiltersAndIncludeTheirScope() {
    registerTestUser();
    long projectId = populatedProject("Export filters " + java.util.UUID.randomUUID());
    long taskId =
        id(
            request(
                HttpMethod.POST,
                "/api/tasks",
                Map.of(
                    "title",
                    "Unique export match",
                    "status",
                    "To Do",
                    "projectId",
                    projectId,
                    "blocked",
                    true,
                    "blockReason",
                    "Review",
                    "labels",
                    List.of("Bug"),
                    "dueAt",
                    Instant.now().minusSeconds(86400).toString(),
                    "assigneeEmail",
                    testEmail)));
    var path =
        "/api/projects/"
            + projectId
            + "/export.csv?status=To"
            + " Do&sprint=backlog&blocked=true&label=Bug&due=overdue&q=Unique&assignee="
            + testEmail;
    var csv = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(cookieHeaders()), String.class);
    assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(csv.getBody())
        .contains("Scope,", "Filters,", "blocked=true", "Unique export match")
        .doesNotContain("Cascade task");
    var pdf =
        rest.exchange(
            path.replace("export.csv", "export.pdf"),
            HttpMethod.GET,
            new HttpEntity<>(cookieHeaders()),
            byte[].class);
    assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(new String(pdf.getBody(), StandardCharsets.ISO_8859_1))
        .contains("Unique export match", "Selected tasks: 1");
    assertThat(
            rest.exchange(
                    "/api/projects/" + projectId + "/export.csv?due=invalid",
                    HttpMethod.GET,
                    new HttpEntity<>(cookieHeaders()),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(taskRepository.findById(taskId)).isPresent();
  }

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
  void movingAStoryMovesItsTasksAndPreservesHistoryAndVersions() {
    registerTestUser();
    long projectId = populatedProject("Story move " + java.util.UUID.randomUUID());
    var task = taskRepository.findByProjectId(projectId).get(0);
    long trashedId =
        id(
            request(
                HttpMethod.POST,
                "/api/tasks",
                Map.of(
                    "title",
                    "Previously trashed",
                    "status",
                    "To Do",
                    "projectId",
                    projectId,
                    "storyId",
                    task.getStoryId(),
                    "sprintId",
                    task.getSprintId())));
    request(HttpMethod.DELETE, "/api/tasks/" + trashedId, null);
    var trashed = taskRepository.findById(trashedId).orElseThrow();
    long target =
        id(
            request(
                HttpMethod.POST,
                "/api/sprints",
                Map.of(
                    "name",
                    "Target",
                    "status",
                    "Planned",
                    "projectId",
                    projectId,
                    "startDate",
                    "2026-10-15",
                    "endDate",
                    "2026-10-28")));
    var story =
        new java.util.HashMap<String, Object>(
            request(HttpMethod.GET, "/api/stories/" + task.getStoryId(), null).getBody());
    story.put("sprintId", target);
    int before = taskHistoryRepository.findByTaskIdOrderByChangedAtDesc(task.getId()).size();
    assertThat(request(HttpMethod.PUT, "/api/stories/" + task.getStoryId(), story).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var moved = taskRepository.findById(task.getId()).orElseThrow();
    assertThat(moved.getSprintId()).isEqualTo(target);
    var stillTrashed = taskRepository.findById(trashedId).orElseThrow();
    assertThat(stillTrashed.getSprintId()).isEqualTo(target);
    assertThat(stillTrashed.getTrashedAt()).isEqualTo(trashed.getTrashedAt());
    assertThat(stillTrashed.getDeletionBatchId()).isEqualTo(trashed.getDeletionBatchId());
    assertThat(moved.getVersion()).isGreaterThan(task.getVersion());
    assertThat(taskHistoryRepository.findByTaskIdOrderByChangedAtDesc(task.getId()))
        .hasSize(before + 1);
    assertThat(
            taskHistoryRepository
                .findByTaskIdOrderByChangedAtDesc(task.getId())
                .get(0)
                .getSprintId())
        .isEqualTo(target);
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/tasks/" + task.getId(),
                    Map.of(
                        "title",
                        "Old editor",
                        "status",
                        "To Do",
                        "projectId",
                        projectId,
                        "version",
                        task.getVersion()))
                .getStatusCode())
        .isEqualTo(HttpStatus.PRECONDITION_FAILED);
    story =
        new java.util.HashMap<>(
            request(HttpMethod.GET, "/api/stories/" + task.getStoryId(), null).getBody());
    story.put("sprintId", null);
    assertThat(request(HttpMethod.PUT, "/api/stories/" + task.getStoryId(), story).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(taskRepository.findById(task.getId()).orElseThrow().getSprintId()).isNull();
    assertThat(taskRepository.findById(trashedId).orElseThrow().getSprintId()).isNull();
    var beforeStale = taskRepository.findById(task.getId()).orElseThrow();
    int historyAfter = taskHistoryRepository.findByTaskIdOrderByChangedAtDesc(task.getId()).size();
    story.put("sprintId", target);
    assertThat(request(HttpMethod.PUT, "/api/stories/" + task.getStoryId(), story).getStatusCode())
        .isEqualTo(HttpStatus.PRECONDITION_FAILED);
    assertThat(taskRepository.findById(task.getId()).orElseThrow().getVersion())
        .isEqualTo(beforeStale.getVersion());
    assertThat(taskHistoryRepository.findByTaskIdOrderByChangedAtDesc(task.getId()))
        .hasSize(historyAfter);
  }

  @Test
  void unplannedStoryTasksCannotBeIndependentlyScheduled() {
    registerTestUser();
    long projectId = populatedProject("Unplanned story " + java.util.UUID.randomUUID());
    var original = taskRepository.findByProjectId(projectId).get(0);
    var story =
        new java.util.HashMap<String, Object>(
            request(HttpMethod.GET, "/api/stories/" + original.getStoryId(), null).getBody());
    story.put("sprintId", null);
    assertThat(
            request(HttpMethod.PUT, "/api/stories/" + original.getStoryId(), story).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    var linked = new java.util.HashMap<String, Object>();
    linked.put("title", "Login form");
    linked.put("status", "To Do");
    linked.put("projectId", projectId);
    linked.put("storyId", original.getStoryId());
    linked.put("sprintId", original.getSprintId());
    assertThat(request(HttpMethod.POST, "/api/tasks", linked).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    var current =
        new java.util.HashMap<String, Object>(
            request(HttpMethod.GET, "/api/tasks/" + original.getId(), null).getBody());
    current.put("sprintId", original.getSprintId());
    int historyBefore =
        taskHistoryRepository.findByTaskIdOrderByChangedAtDesc(original.getId()).size();
    assertThat(request(HttpMethod.PUT, "/api/tasks/" + original.getId(), current).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(taskRepository.findById(original.getId()).orElseThrow().getSprintId()).isNull();
    assertThat(taskHistoryRepository.findByTaskIdOrderByChangedAtDesc(original.getId()))
        .hasSize(historyBefore);

    linked.put("sprintId", null);
    assertThat(request(HttpMethod.POST, "/api/tasks", linked).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    linked.put("storyId", null);
    linked.put("sprintId", original.getSprintId());
    assertThat(request(HttpMethod.POST, "/api/tasks", linked).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void pointEditsRecordHistoryWithoutChangingWorkflowStatus() {
    registerTestUser();
    long projectId = populatedProject("Point history " + java.util.UUID.randomUUID());
    var task = taskRepository.findByProjectId(projectId).get(0);
    var body =
        new java.util.HashMap<String, Object>(
            request(HttpMethod.GET, "/api/tasks/" + task.getId(), null).getBody());
    int before = taskHistoryRepository.findByTaskIdOrderByChangedAtDesc(task.getId()).size();
    body.put("storyPoints", 8);
    assertThat(request(HttpMethod.PUT, "/api/tasks/" + task.getId(), body).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var history = taskHistoryRepository.findByTaskIdOrderByChangedAtDesc(task.getId());
    assertThat(history).hasSize(before + 1);
    assertThat(history.get(0).getStoryPoints()).isEqualTo(8);
    assertThat(history.get(0).getStatus()).isEqualTo(task.getStatus());
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
  void deletionRoutesShareTrashAndOnlyOwnerCanPurge() {
    registerTestUser();
    long projectId = populatedProject("Lifecycle roles " + java.util.UUID.randomUUID());
    var task = taskRepository.findByProjectId(projectId).get(0);
    String owner = sessionCookie;
    String member = "member-" + java.util.UUID.randomUUID() + "@example.com";
    String memberCookie =
        registerUser(member).getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
    sessionCookie = owner;
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/projects/" + projectId + "/members",
                    Map.of("email", member, "role", "Member"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    sessionCookie = memberCookie;
    assertThat(request(HttpMethod.DELETE, "/api/tasks/" + task.getId(), null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(taskRepository.findById(task.getId()).orElseThrow().getTrashedAt()).isNotNull();
    assertThat(taskHistoryRepository.findByTaskIdOrderByChangedAtDesc(task.getId())).isNotEmpty();
    assertThat(
            request(HttpMethod.DELETE, "/api/workspace/trash/task/" + task.getId(), null)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    sessionCookie = owner;
    assertThat(
            request(HttpMethod.DELETE, "/api/workspace/trash/task/" + task.getId(), null)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(taskRepository.findById(task.getId())).isEmpty();
    assertThat(
            activityEventRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(e -> "TASK_PURGED".equals(e.getEventType()))
                .findFirst()
                .orElseThrow()
                .getActorUserId())
        .isNotNull();
  }

  @Test
  void restoringSprintPreservesPreviouslyTrashedChildren() {
    registerTestUser();
    long projectId = populatedProject("Lifecycle batch " + java.util.UUID.randomUUID());
    var first = taskRepository.findByProjectId(projectId).get(0);
    long sprintId = first.getSprintId();
    long second =
        id(
            request(
                HttpMethod.POST,
                "/api/tasks",
                Map.of(
                    "title",
                    "Already trashed",
                    "status",
                    "To Do",
                    "projectId",
                    projectId,
                    "sprintId",
                    sprintId)));
    request(HttpMethod.DELETE, "/api/tasks/" + second, null);
    assertThat(request(HttpMethod.DELETE, "/api/sprints/" + sprintId, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(taskRepository.findById(first.getId()).orElseThrow().getTrashedAt()).isNotNull();
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/workspace/trash/task/" + first.getId(),
                    Map.of("restore", true))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/workspace/trash/sprint/" + sprintId,
                    Map.of("restore", true))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(taskRepository.findById(first.getId()).orElseThrow().getTrashedAt()).isNull();
    assertThat(taskRepository.findById(second).orElseThrow().getTrashedAt()).isNotNull();
  }

  @Test
  void taskMutationsRequireCurrentVersionAndPreserveActor() {
    registerTestUser();
    long projectId = populatedProject("Preconditions " + java.util.UUID.randomUUID());
    var task = taskRepository.findByProjectId(projectId).get(0);
    String path = "/api/tasks/" + task.getId() + "/block?reason=Waiting";
    var missing = csrfHeaders();
    assertThat(
            rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(null, missing), Map.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
    var stale = csrfHeaders();
    stale.set("If-Match", "\"999999\"");
    assertThat(
            rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(null, stale), Map.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.PRECONDITION_FAILED);
    assertThat(request(HttpMethod.PUT, path, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    var event =
        activityEventRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
            .filter(e -> "TASK_BLOCKED".equals(e.getEventType()))
            .findFirst()
            .orElseThrow();
    assertThat(event.getActorEmail()).isEqualTo(testEmail);
    assertThat(event.getActorUserId())
        .isEqualTo(userRepository.findByEmail(testEmail).orElseThrow().getId());
    assertThat(event.getEntityId()).isEqualTo(task.getId());
  }

  @Test
  void taskPointsAndCreationTimesAreControlledByBackend() {
    registerTestUser();
    long projectId =
        id(
            request(
                HttpMethod.POST,
                "/api/projects",
                Map.of("name", "Validation " + java.util.UUID.randomUUID())));
    for (Object points : java.util.List.of(-1, 1001, 1.5))
      assertThat(
              request(
                      HttpMethod.POST,
                      "/api/tasks",
                      Map.of(
                          "title",
                          "Invalid",
                          "status",
                          "To Do",
                          "projectId",
                          projectId,
                          "storyPoints",
                          points))
                  .getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    Instant before = Instant.now().minusSeconds(1);
    long taskId =
        id(
            request(
                HttpMethod.POST,
                "/api/tasks",
                Map.of(
                    "title",
                    "Server clock",
                    "status",
                    "To Do",
                    "projectId",
                    projectId,
                    "storyPoints",
                    1000,
                    "createdAt",
                    "2000-01-01T00:00:00Z",
                    "completedAt",
                    "2000-01-01T00:00:00Z")));
    var saved = taskRepository.findById(taskId).orElseThrow();
    assertThat(saved.getCreatedAt()).isAfter(before);
    assertThat(saved.getCompletedAt()).isNull();
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> jdbc.update("update task set story_points=-1 where id=?", taskId))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void taskMutationAndHistoryRollbackTogether() {
    registerTestUser();
    long projectId = populatedProject("Mutation rollback " + java.util.UUID.randomUUID());
    var task = taskRepository.findByProjectId(projectId).get(0);
    String originalStatus = task.getStatus();
    int historyBefore = taskHistoryRepository.findByTaskIdOrderByChangedAtDesc(task.getId()).size();
    var changes = new com.sprintflowinsight.model.Task("Changed", "", "Done");
    changes.setProjectId(projectId);
    changes.setStoryPoints(5);
    new org.springframework.transaction.support.TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              taskService.updateTask(task, changes, testEmail);
              tx.setRollbackOnly();
            });
    assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus())
        .isEqualTo(originalStatus);
    assertThat(taskHistoryRepository.findByTaskIdOrderByChangedAtDesc(task.getId()))
        .hasSize(historyBefore);
  }

  @Test
  void databaseRejectsASecondActiveSprintInTheSameProject() {
    registerTestUser();
    long projectId = populatedProject("Unique sprint " + java.util.UUID.randomUUID());
    long first = taskRepository.findByProjectId(projectId).get(0).getSprintId();
    jdbc.update("update sprint set status='Active' where id=?", first);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into sprint(name,status,total_story_points,project_id,version) values"
                        + " ('Duplicate','Active',0,?,0)",
                    projectId))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void twoEditorsCannotSilentlyOverwriteEachOther() throws Exception {
    registerTestUser();
    long projectId = populatedProject("Concurrent task " + java.util.UUID.randomUUID());
    var task = taskRepository.findByProjectId(projectId).get(0);
    HttpHeaders first = csrfHeaders();
    first.setContentType(MediaType.APPLICATION_JSON);
    first.set("If-Match", "\"" + task.getVersion() + "\"");
    HttpHeaders second = new HttpHeaders();
    second.addAll(first);
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    var gate = new java.util.concurrent.CountDownLatch(1);
    try {
      var a =
          executor.submit(
              () -> {
                gate.await();
                return rest.exchange(
                        "/api/tasks/" + task.getId(),
                        HttpMethod.PUT,
                        new HttpEntity<>(
                            Map.of(
                                "title", "First editor", "status", "To Do", "projectId", projectId),
                            first),
                        Map.class)
                    .getStatusCode();
              });
      var b =
          executor.submit(
              () -> {
                gate.await();
                return rest.exchange(
                        "/api/tasks/" + task.getId(),
                        HttpMethod.PUT,
                        new HttpEntity<>(
                            Map.of(
                                "title",
                                "Second editor",
                                "status",
                                "To Do",
                                "projectId",
                                projectId),
                            second),
                        Map.class)
                    .getStatusCode();
              });
      gate.countDown();
      assertThat(
              java.util.List.of(
                  a.get(15, java.util.concurrent.TimeUnit.SECONDS),
                  b.get(15, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.PRECONDITION_FAILED);
    } finally {
      executor.shutdownNow();
    }
  }

  private String sessionCookie;
  private String testEmail;

  private void registerTestUser() {
    String email = "test-" + java.util.UUID.randomUUID() + "@example.com";
    testEmail = email;
    ResponseEntity<Map> response = registerUser(email);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    sessionCookie = cookie == null ? null : cookie.split(";", 2)[0];
  }

  @Autowired private TestRestTemplate rest;

  @Autowired private UserRepository userRepository;

  @Autowired private NotificationService notificationService;

  @Autowired private TaskRepository taskRepository;

  @Autowired private TaskStatusHistoryRepository taskHistoryRepository;

  @Autowired private ProjectRepository projectRepository;

  @Autowired private ActivityEventRepository activityEventRepository;

  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired private com.sprintflowinsight.project.ProjectDeletionService projectDeletion;
  @Autowired private org.springframework.transaction.PlatformTransactionManager transactions;
  @Autowired private com.sprintflowinsight.service.TaskService taskService;

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

  @Autowired private com.sprintflowinsight.auth.UserEmailService userEmailService;

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
  void permanentTaskDeletionPreservesFilesWhenTransactionRollsBack() {
    registerTestUser();
    long projectId = populatedProject("Task rollback " + java.util.UUID.randomUUID());
    long taskId = taskRepository.findByProjectId(projectId).get(0).getId();
    String key =
        jdbc.queryForObject(
            "select storage_key from task_attachment where task_id = ? and storage_key is not null",
            String.class,
            taskId);
    java.nio.file.Path file = java.nio.file.Path.of("data", "attachments", key).toAbsolutePath();
    assertThat(file).exists();
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                new org.springframework.transaction.support.TransactionTemplate(transactions)
                    .executeWithoutResult(
                        status -> {
                          taskService.deleteTask(taskId);
                          throw new IllegalStateException("Simulated rollback");
                        }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(taskRepository.existsById(taskId)).isTrue();
    assertThat(file).exists();
    taskService.deleteTask(taskId);
    assertThat(taskRepository.existsById(taskId)).isFalse();
    assertThat(file).doesNotExist();
    assertThat(request(HttpMethod.DELETE, "/api/projects/" + projectId, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void projectAndStoryFlowWorksEndToEnd() {
    registerTestUser();
    ResponseEntity<Map> project =
        request(
            HttpMethod.POST,
            "/api/projects",
            Map.of(
                "name", "Project verification",
                "description", "Integration test",
                "ownerEmail", "verify@example.com"));
    assertThat(project.getStatusCode()).isEqualTo(HttpStatus.OK);
    long projectId = id(project);
    ResponseEntity<Map> ownerRole =
        rest.exchange(
            "/api/projects/" + projectId + "/role",
            HttpMethod.GET,
            new HttpEntity<>(cookieHeaders()),
            Map.class);
    assertThat(ownerRole.getBody().get("role")).isEqualTo("Owner");
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/projects/" + projectId,
                    Map.of(
                        "name", "Project verification updated",
                        "description", "Updated integration test",
                        "ownerEmail", "verify@example.com"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    ResponseEntity<Map> story =
        request(
            HttpMethod.POST,
            "/api/stories",
            Map.of(
                "title",
                "Story verification",
                "description",
                "",
                "acceptanceCriteria",
                "It can be created",
                "storyPoints",
                3,
                "priorityId",
                2,
                "businessValue",
                4,
                "projectId",
                projectId));
    assertThat(story.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/stories/" + id(story),
                    Map.of(
                        "title",
                        "Story verification updated",
                        "description",
                        "Updated",
                        "acceptanceCriteria",
                        "It can be updated",
                        "storyPoints",
                        5,
                        "priorityId",
                        1,
                        "businessValue",
                        5,
                        "projectId",
                        projectId))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    assertThat(request(HttpMethod.DELETE, "/api/projects/" + projectId, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from user_stories where project_id = ?", Long.class, projectId))
        .isZero();
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

  private long projectRows(String table, long projectId) {
    return jdbc.queryForObject(
        "select count(*) from " + table + " where project_id = ?", Long.class, projectId);
  }

  private long populatedProject(String name) {
    long projectId = id(request(HttpMethod.POST, "/api/projects", Map.of("name", name)));
    long sprintId =
        id(
            request(
                HttpMethod.POST,
                "/api/sprints",
                Map.of(
                    "name",
                    "Cascade sprint",
                    "status",
                    "Planned",
                    "projectId",
                    projectId,
                    "startDate",
                    "2026-10-01",
                    "endDate",
                    "2026-10-14")));
    long storyId =
        id(
            request(
                HttpMethod.POST,
                "/api/stories",
                Map.of(
                    "title",
                    "Cascade story",
                    "acceptanceCriteria",
                    "Deletes with project",
                    "storyPoints",
                    3,
                    "priorityId",
                    2,
                    "sprintId",
                    sprintId,
                    "projectId",
                    projectId)));
    long taskId =
        id(
            request(
                HttpMethod.POST,
                "/api/tasks",
                Map.of(
                    "title",
                    "Cascade task",
                    "status",
                    "To Do",
                    "projectId",
                    projectId,
                    "sprintId",
                    sprintId,
                    "storyId",
                    storyId,
                    "dueAt",
                    Instant.now().minusSeconds(86400).toString())));
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/tasks/" + taskId + "/comments",
                    Map.of("message", "Cascade comment"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/tasks/" + taskId + "/attachments",
                    Map.of("name", "Cascade link", "url", "https://example.com"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
    multipart.add(
        "file",
        new ByteArrayResource(TestFiles.PDF) {
          @Override
          public String getFilename() {
            return "cascade.pdf";
          }
        });
    HttpHeaders headers = csrfHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    assertThat(
            rest.exchange(
                    "/api/tasks/" + taskId + "/attachments/upload",
                    HttpMethod.POST,
                    new HttpEntity<>(multipart, headers),
                    Map.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    notificationService.sendDueDateReminders();
    return projectId;
  }

  @Test
  void sprintAndTaskFlowWorksEndToEnd() {
    registerTestUser();
    ResponseEntity<Map> project =
        request(
            HttpMethod.POST,
            "/api/projects",
            Map.of(
                "name",
                "Delivery verification",
                "description",
                "Integration test",
                "ownerEmail",
                "verify@example.com"));
    assertThat(project.getStatusCode()).isEqualTo(HttpStatus.OK);
    long projectId = id(project);
    ResponseEntity<Map> sprint =
        request(
            HttpMethod.POST,
            "/api/sprints",
            Map.of(
                "name",
                "Sprint verification",
                "goal",
                "Verify the flow",
                "status",
                "Planned",
                "projectId",
                projectId,
                "totalStoryPoints",
                5,
                "startDate",
                "2026-10-01",
                "endDate",
                "2026-10-14"));
    assertThat(sprint.getStatusCode()).isEqualTo(HttpStatus.OK);
    long sprintId = id(sprint);
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/sprints/" + sprintId,
                    Map.of(
                        "name",
                        "Sprint verification updated",
                        "goal",
                        "Verify updates",
                        "status",
                        "Planned",
                        "projectId",
                        projectId,
                        "totalStoryPoints",
                        8,
                        "startDate",
                        "2026-10-02",
                        "endDate",
                        "2026-10-15"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/sprints",
                    Map.of(
                        "name",
                        "Invalid dates",
                        "status",
                        "Planned",
                        "projectId",
                        projectId,
                        "startDate",
                        "2026-10-15",
                        "endDate",
                        "2026-10-14"))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            request(HttpMethod.PUT, "/api/sprints/" + sprintId + "/status?status=Active", null)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    ResponseEntity<Map> secondActiveSprint =
        request(
            HttpMethod.POST,
            "/api/sprints",
            Map.of(
                "name",
                "Second active sprint",
                "status",
                "Active",
                "projectId",
                projectId,
                "startDate",
                "2026-10-16",
                "endDate",
                "2026-10-29"));
    assertThat(secondActiveSprint.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            request(HttpMethod.PUT, "/api/sprints/" + sprintId + "/status?status=Unknown", null)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    ResponseEntity<Map> story =
        request(
            HttpMethod.POST,
            "/api/stories",
            Map.of(
                "title",
                "Task parent story",
                "description",
                "Same project",
                "acceptanceCriteria",
                "Task is linked safely",
                "storyPoints",
                3,
                "priorityId",
                2,
                "businessValue",
                5,
                "sprintId",
                sprintId,
                "projectId",
                projectId));
    assertThat(story.getStatusCode()).isEqualTo(HttpStatus.OK);
    long storyId = id(story);

    ResponseEntity<Map> task =
        request(
            HttpMethod.POST,
            "/api/tasks",
            Map.of(
                "title",
                "Task verification",
                "description",
                "Integration test",
                "status",
                "To Do",
                "projectId",
                projectId,
                "storyId",
                storyId,
                "sprintId",
                sprintId,
                "storyPoints",
                3));
    assertThat(task.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/stories",
                    Map.of(
                        "title",
                        "Invalid estimate",
                        "description",
                        "",
                        "acceptanceCriteria",
                        "Points are restricted",
                        "storyPoints",
                        4,
                        "priorityId",
                        2,
                        "projectId",
                        projectId))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/tasks",
                    Map.of(
                        "title",
                        "Mismatched sprint task",
                        "description",
                        "",
                        "status",
                        "To Do",
                        "projectId",
                        projectId,
                        "storyId",
                        storyId,
                        "storyPoints",
                        1))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    long taskId = id(task);
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/tasks/" + taskId,
                    Map.of(
                        "title",
                        "Task verification updated",
                        "description",
                        "Updated task",
                        "status",
                        "In Progress",
                        "projectId",
                        projectId,
                        "storyId",
                        storyId,
                        "sprintId",
                        sprintId,
                        "storyPoints",
                        5))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(taskRepository.findById(taskId).orElseThrow().getStartedAt()).isNotNull();
    assertThat(taskHistoryRepository.findByTaskIdOrderByChangedAtDesc(taskId)).hasSize(2);
    ResponseEntity<Map> sprintMetrics =
        request(
            HttpMethod.GET, "/api/projects/" + projectId + "/metrics?sprintId=" + sprintId, null);
    assertThat(sprintMetrics.getStatusCode()).isEqualTo(HttpStatus.OK);
    Map<?, ?> businessValueMetrics = (Map<?, ?>) sprintMetrics.getBody().get("businessValue");
    assertThat(businessValueMetrics.get("expectedValue")).isEqualTo(5);
    assertThat(businessValueMetrics.get("deliveredValue")).isEqualTo(0);
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/tasks/" + taskId,
                    Map.of(
                        "title",
                        "Task verification completed",
                        "description",
                        "Meets Definition of Done",
                        "status",
                        "Done",
                        "projectId",
                        projectId,
                        "storyId",
                        storyId,
                        "sprintId",
                        sprintId,
                        "storyPoints",
                        5))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(taskRepository.findById(taskId).orElseThrow().getCompletedAt()).isNotNull();
    assertThat(taskHistoryRepository.findByTaskIdOrderByChangedAtDesc(taskId)).hasSize(3);
    ResponseEntity<Map> afterCompletionMetrics =
        request(
            HttpMethod.GET, "/api/projects/" + projectId + "/metrics?sprintId=" + sprintId, null);
    assertThat(
            ((Map<?, ?>) afterCompletionMetrics.getBody().get("businessValue"))
                .get("deliveredValue"))
        .isEqualTo(5);
    assertThat(request(HttpMethod.DELETE, "/api/stories/" + storyId, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(taskRepository.findById(taskId).orElseThrow().getStoryId()).isEqualTo(storyId);
    assertThat(taskRepository.findById(taskId).orElseThrow().getTrashedAt()).isNotNull();
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/workspace/trash/story/" + storyId,
                    Map.of("restore", true))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/tasks",
                    Map.of(
                        "title",
                        "Invalid cross-project task",
                        "status",
                        "To Do",
                        "projectId",
                        projectId,
                        "sprintId",
                        sprintId + 1000))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            request(HttpMethod.PUT, "/api/tasks/" + taskId + "/block?reason=Waiting", null)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(request(HttpMethod.PUT, "/api/tasks/" + taskId + "/unblock", null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(request(HttpMethod.DELETE, "/api/tasks/" + taskId, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(request(HttpMethod.DELETE, "/api/sprints/" + sprintId, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
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

  @Test
  void completedSprintDeliverySurvivesTaskMovesAndExcludesLateCompletions() {
    registerTestUser();
    long projectId =
        id(request(HttpMethod.POST, "/api/projects", Map.of("name", "Historical delivery")));
    long sprintId =
        id(
            request(
                HttpMethod.POST,
                "/api/sprints",
                Map.of(
                    "name",
                    "First sprint",
                    "status",
                    "Active",
                    "projectId",
                    projectId,
                    "totalStoryPoints",
                    5,
                    "startDate",
                    java.time.LocalDate.now().minusDays(7).toString(),
                    "endDate",
                    java.time.LocalDate.now().plusDays(7).toString())));
    long taskId =
        id(
            request(
                HttpMethod.POST,
                "/api/tasks",
                Map.of(
                    "title",
                    "Delivered task",
                    "status",
                    "Done",
                    "projectId",
                    projectId,
                    "sprintId",
                    sprintId,
                    "storyPoints",
                    5)));
    assertThat(
            request(HttpMethod.PUT, "/api/sprints/" + sprintId + "/status?status=Completed", null)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    long nextSprintId =
        id(
            request(
                HttpMethod.POST,
                "/api/sprints",
                Map.of(
                    "name",
                    "Next sprint",
                    "status",
                    "Active",
                    "projectId",
                    projectId,
                    "totalStoryPoints",
                    13,
                    "startDate",
                    java.time.LocalDate.now().toString(),
                    "endDate",
                    java.time.LocalDate.now().plusDays(14).toString())));
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/tasks/" + taskId,
                    Map.of(
                        "title",
                        "Reopened and moved task",
                        "status",
                        "In Progress",
                        "projectId",
                        projectId,
                        "sprintId",
                        nextSprintId,
                        "storyPoints",
                        13))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/tasks",
                    Map.of(
                        "title",
                        "Completion after closure",
                        "status",
                        "Done",
                        "projectId",
                        projectId,
                        "sprintId",
                        sprintId,
                        "storyPoints",
                        8))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    long otherProjectId =
        id(request(HttpMethod.POST, "/api/projects", Map.of("name", "Other historical project")));
    long otherSprintId =
        id(
            request(
                HttpMethod.POST,
                "/api/sprints",
                Map.of(
                    "name",
                    "Other sprint",
                    "status",
                    "Planned",
                    "projectId",
                    otherProjectId,
                    "startDate",
                    java.time.LocalDate.now().minusDays(7).toString(),
                    "endDate",
                    java.time.LocalDate.now().plusDays(7).toString())));
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/tasks",
                    Map.of(
                        "title",
                        "Other delivery",
                        "status",
                        "Done",
                        "projectId",
                        otherProjectId,
                        "sprintId",
                        otherSprintId,
                        "storyPoints",
                        21))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/sprints/" + otherSprintId + "/status?status=Completed",
                    null)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    ResponseEntity<Map> metrics =
        request(HttpMethod.GET, "/api/projects/" + projectId + "/metrics", null);
    assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.OK);
    java.util.List<?> snapshots = (java.util.List<?>) metrics.getBody().get("completedSprints");
    assertThat(snapshots).hasSize(1);
    Map<?, ?> snapshot = (Map<?, ?>) snapshots.get(0);
    assertThat(((Number) snapshot.get("id")).longValue()).isEqualTo(sprintId);
    assertThat(snapshot.get("actualStoryPoints")).isEqualTo(5);
    assertThat(snapshot.get("completedTasks")).isEqualTo(1);
    assertThat(request(HttpMethod.DELETE, "/api/projects/" + projectId, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(request(HttpMethod.DELETE, "/api/projects/" + otherProjectId, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void metricsReadsStayIsolatedBetweenProjects() {
    registerTestUser();
    ResponseEntity<Map> firstProject =
        request(
            HttpMethod.POST,
            "/api/projects",
            Map.of(
                "name", "Metrics Alpha", "description", "", "ownerEmail", "ignored@example.com"));
    ResponseEntity<Map> secondProject =
        request(
            HttpMethod.POST,
            "/api/projects",
            Map.of("name", "Metrics Beta", "description", "", "ownerEmail", "ignored@example.com"));
    long firstProjectId = id(firstProject);
    long secondProjectId = id(secondProject);
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/tasks",
                    Map.of(
                        "title",
                        "Alpha delivery",
                        "status",
                        "Done",
                        "projectId",
                        firstProjectId,
                        "storyPoints",
                        5))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/tasks",
                    Map.of(
                        "title",
                        "Beta delivery",
                        "status",
                        "Done",
                        "projectId",
                        secondProjectId,
                        "storyPoints",
                        13))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    ResponseEntity<Map> firstMetrics =
        rest.exchange(
            "/api/projects/" + firstProjectId + "/metrics",
            HttpMethod.GET,
            new HttpEntity<>(cookieHeaders()),
            Map.class);
    ResponseEntity<Map> secondMetrics =
        rest.exchange(
            "/api/projects/" + secondProjectId + "/metrics",
            HttpMethod.GET,
            new HttpEntity<>(cookieHeaders()),
            Map.class);

    assertThat(firstMetrics.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(firstMetrics.getBody())
        .containsEntry("taskCount", 1)
        .containsEntry("velocityStoryPoints", 5);
    assertThat(secondMetrics.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(secondMetrics.getBody())
        .containsEntry("taskCount", 1)
        .containsEntry("velocityStoryPoints", 13);
  }

  @Test
  void taskCommentsCreateAnOwnerNotification() {
    registerTestUser();
    String ownerCookie = sessionCookie;
    ResponseEntity<Map> project =
        request(
            HttpMethod.POST,
            "/api/projects",
            Map.of(
                "name",
                "Collaboration verification",
                "description",
                "",
                "ownerEmail",
                "owner@example.com"));
    long projectId = id(project);
    ResponseEntity<Map> task =
        request(
            HttpMethod.POST,
            "/api/tasks",
            Map.of(
                "title",
                "Discuss delivery",
                "description",
                "",
                "status",
                "To Do",
                "projectId",
                projectId));
    long taskId = id(task);

    String memberEmail = "member-" + java.util.UUID.randomUUID() + "@example.com";
    ResponseEntity<Map> memberRegistration = registerUser(memberEmail);
    String memberCookie =
        memberRegistration.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
    sessionCookie = ownerCookie;
    ResponseEntity<Map> invited =
        request(
            HttpMethod.POST,
            "/api/projects/" + projectId + "/members",
            Map.of("email", memberEmail, "role", "Member"));
    assertThat(invited.getStatusCode()).isEqualTo(HttpStatus.OK);
    sessionCookie = memberCookie;
    ResponseEntity<Map> comment =
        request(
            HttpMethod.POST,
            "/api/tasks/" + taskId + "/comments",
            Map.of("message", "Ready for review"));
    assertThat(comment.getStatusCode()).isEqualTo(HttpStatus.OK);
    HttpHeaders ownerHeaders = new HttpHeaders();
    ownerHeaders.set(HttpHeaders.COOKIE, ownerCookie);
    assertThat(
            rest.exchange(
                    "/api/tasks/" + taskId + "/comments",
                    HttpMethod.GET,
                    new HttpEntity<>(ownerHeaders),
                    Object[].class)
                .getBody())
        .hasSize(1);
    assertThat(
            rest.exchange(
                    "/api/notifications",
                    HttpMethod.GET,
                    new HttpEntity<>(ownerHeaders),
                    Object[].class)
                .getBody())
        .hasSize(1);
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

  @Test
  void activityUpdatesCanBeEditedAndDeletedOnlyByProjectAdmins() {
    registerTestUser();
    String ownerCookie = sessionCookie;
    ResponseEntity<Map> project =
        request(
            HttpMethod.POST,
            "/api/projects",
            Map.of(
                "name",
                "Activity editing",
                "description",
                "",
                "ownerEmail",
                "ignored@example.com"));
    long projectId = id(project);
    request(
        HttpMethod.POST,
        "/api/tasks",
        Map.of("title", "Original activity", "status", "To Do", "projectId", projectId));
    var original = activityEventRepository.findByProjectIdOrderByCreatedAtDesc(projectId).get(0);

    String viewerEmail = "activity-viewer-" + java.util.UUID.randomUUID() + "@example.com";
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
    String eventUrl = "/api/projects/" + projectId + "/activity/" + original.getId();
    assertThat(
            request(HttpMethod.PUT, eventUrl, Map.of("summary", "Unauthorized edit"))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(request(HttpMethod.DELETE, eventUrl, null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            request(HttpMethod.DELETE, "/api/projects/" + projectId + "/activity", null)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    sessionCookie = ownerCookie;
    ResponseEntity<Map> edited =
        request(HttpMethod.PUT, eventUrl, Map.of("summary", "Corrected activity text"));
    assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(edited.getBody()).containsEntry("summary", "Corrected activity text");
    var updated = activityEventRepository.findById(original.getId()).orElseThrow();
    assertThat(updated.getEventType()).isEqualTo(original.getEventType());
    assertThat(updated.getActorEmail()).isEqualTo(original.getActorEmail());
    assertThat(updated.getCreatedAt()).isEqualTo(original.getCreatedAt());
    assertThat(request(HttpMethod.PUT, eventUrl, Map.of("summary", " ")).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(request(HttpMethod.DELETE, eventUrl, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(activityEventRepository.findById(original.getId())).isEmpty();
    var projectCreatedEvent =
        activityEventRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
            .filter(event -> "PROJECT_CREATED".equals(event.getEventType()))
            .findFirst()
            .orElseThrow();
    String projectEventUrl =
        "/api/projects/" + projectId + "/activity/" + projectCreatedEvent.getId();
    assertThat(request(HttpMethod.DELETE, projectEventUrl, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(activityEventRepository.findById(projectCreatedEvent.getId())).isEmpty();
    request(
        HttpMethod.POST,
        "/api/tasks",
        Map.of("title", "Another activity", "status", "To Do", "projectId", projectId));
    assertThat(activityEventRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).isNotEmpty();
    assertThat(
            request(HttpMethod.DELETE, "/api/projects/" + projectId + "/activity", null)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(activityEventRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).isEmpty();
  }

  @Test
  void remindersAttachmentsBurndownAndCsvWorkTogether() {
    registerTestUser();
    ResponseEntity<Map> project =
        request(
            HttpMethod.POST,
            "/api/projects",
            Map.of(
                "name",
                "Feature verification",
                "description",
                "",
                "ownerEmail",
                "ignored@example.com"));
    assertThat(project.getStatusCode())
        .as("project response: %s", project.getBody())
        .isEqualTo(HttpStatus.OK);
    long projectId = id(project);
    String due = Instant.now().minusSeconds(86400).toString();
    ResponseEntity<Map> task =
        request(
            HttpMethod.POST,
            "/api/tasks",
            Map.of(
                "title",
                "Overdue upload task",
                "status",
                "To Do",
                "projectId",
                projectId,
                "storyPoints",
                3,
                "dueAt",
                due));
    long taskId = id(task);
    notificationService.sendDueDateReminders();
    ResponseEntity<Object[]> notices =
        rest.exchange(
            "/api/notifications",
            HttpMethod.GET,
            new HttpEntity<>(cookieHeaders()),
            Object[].class);
    assertThat(notices.getBody()).hasSize(1);

    MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
    multipart.add(
        "file",
        new ByteArrayResource(TestFiles.PDF) {
          @Override
          public String getFilename() {
            return "test.pdf";
          }
        });
    HttpHeaders uploadHeaders = csrfHeaders();
    uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
    ResponseEntity<Map> upload =
        rest.exchange(
            "/api/tasks/" + taskId + "/attachments/upload",
            HttpMethod.POST,
            new HttpEntity<>(multipart, uploadHeaders),
            Map.class);
    assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.OK);
    long attachmentId = ((Number) upload.getBody().get("id")).longValue();
    ResponseEntity<byte[]> file =
        rest.exchange(
            "/api/tasks/" + taskId + "/attachments/" + attachmentId + "/file",
            HttpMethod.GET,
            new HttpEntity<>(cookieHeaders()),
            byte[].class);
    assertThat(file.getBody()).isEqualTo(TestFiles.PDF);
    assertThat(
            rest.exchange(
                    "/api/tasks/" + taskId + "/attachments/" + attachmentId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(csrfHeaders()),
                    Void.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<Map> metrics =
        rest.exchange(
            "/api/projects/" + projectId + "/metrics",
            HttpMethod.GET,
            new HttpEntity<>(cookieHeaders()),
            Map.class);
    assertThat((java.util.List<?>) metrics.getBody().get("burndown")).hasSize(14);
    HttpHeaders exportHeaders = cookieHeaders();
    ResponseEntity<String> csv =
        rest.exchange(
            "/api/projects/" + projectId + "/export.csv",
            HttpMethod.GET,
            new HttpEntity<>(exportHeaders),
            String.class);
    assertThat(csv.getBody()).contains("Overdue upload task", "Due date");
    ResponseEntity<byte[]> pdf =
        rest.exchange(
            "/api/projects/" + projectId + "/export.pdf",
            HttpMethod.GET,
            new HttpEntity<>(exportHeaders),
            byte[].class);
    String pdfText = new String(pdf.getBody(), StandardCharsets.ISO_8859_1);
    assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(pdf.getHeaders().getContentType().toString()).startsWith("application/pdf");
    assertThat(pdfText)
        .startsWith("%PDF-1.4")
        .contains(
            "/Type /Catalog",
            "/Type /Page",
            "/Contents 4 0 R",
            "DELIVERY SUMMARY",
            "Overdue upload task");
  }

  @Test
  void templatesChecklistsLabelsCommentsAndNotificationsRespectPermissions() {
    registerTestUser();
    String ownerCookie = sessionCookie;
    String ownerEmail = testEmail;
    ResponseEntity<Map> template =
        request(
            HttpMethod.POST,
            "/api/projects/templates",
            Map.of(
                "name",
                "Template " + java.util.UUID.randomUUID(),
                "template",
                "Scrum",
                "description",
                "Feature QA"));
    assertThat(template.getStatusCode()).isEqualTo(HttpStatus.OK);
    long projectId = id(template);
    assertThat(projectRows("task", projectId)).isEqualTo(3);
    assertThat(projectRows("sprint", projectId)).isEqualTo(1);
    assertThat(projectRows("user_stories", projectId)).isEqualTo(1);
    long taskId = taskRepository.findByProjectId(projectId).get(0).getId();
    var task = taskRepository.findById(taskId).orElseThrow();
    java.util.List<Map<String, Object>> checklist =
        java.util.List.of(
            Map.of("id", "step-1", "label", "Review", "completed", false, "required", true));
    Map<String, Object> changes = new java.util.HashMap<>();
    changes.put("title", task.getTitle());
    changes.put("projectId", projectId);
    changes.put("status", "To Do");
    changes.put("labels", java.util.List.of("Bug", "Frontend"));
    changes.put("checklist", checklist);
    assertThat(request(HttpMethod.PUT, "/api/tasks/" + taskId, changes).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(taskRepository.findById(taskId).orElseThrow().getLabels())
        .containsExactly("Bug", "Frontend");
    changes.put("status", "Done");
    assertThat(request(HttpMethod.PUT, "/api/tasks/" + taskId, changes).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    changes.put(
        "checklist",
        java.util.List.of(
            Map.of("id", "step-1", "label", "Review", "completed", true, "required", true)));
    assertThat(request(HttpMethod.PUT, "/api/tasks/" + taskId, changes).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    changes.put("labels", java.util.List.of("Bug", "Bug"));
    assertThat(request(HttpMethod.PUT, "/api/tasks/" + taskId, changes).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    changes.put("labels", java.util.List.of("Bug"));
    String memberEmail = "collab-" + java.util.UUID.randomUUID() + "@example.com";
    ResponseEntity<Map> member = registerUser(memberEmail);
    String memberCookie = member.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
    sessionCookie = ownerCookie;
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/projects/" + projectId + "/members",
                    Map.of("email", memberEmail, "role", "Member"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    changes.put("assigneeEmail", memberEmail);
    assertThat(request(HttpMethod.PUT, "/api/tasks/" + taskId, changes).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    long ownerComment =
        id(
            request(
                HttpMethod.POST,
                "/api/tasks/" + taskId + "/comments",
                Map.of("message", "Owner note")));
    assertThat(
            request(HttpMethod.POST, "/api/tasks/" + taskId + "/comments", Map.of("message", " "))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/tasks/" + taskId + "/comments",
                    Map.of("message", "x".repeat(1001)))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    sessionCookie = memberCookie;
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/tasks/" + taskId + "/comments/" + ownerComment,
                    Map.of("message", "Unauthorized"))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    long ownComment =
        id(
            request(
                HttpMethod.POST,
                "/api/tasks/" + taskId + "/comments",
                Map.of("message", "Member note")));
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/tasks/" + taskId + "/comments/" + ownComment,
                    Map.of("message", "Updated member note"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/tasks/" + taskId + "/comments/" + ownComment,
                    Map.of("message", "Stale", "version", 999))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    var notices =
        rest.exchange(
            "/api/notifications",
            HttpMethod.GET,
            new HttpEntity<>(cookieHeaders()),
            Object[].class);
    assertThat(notices.getBody()).hasSize(2);
    assertThat(request(HttpMethod.PUT, "/api/notifications/read", null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    Object[] read =
        rest.exchange(
                "/api/notifications",
                HttpMethod.GET,
                new HttpEntity<>(cookieHeaders()),
                Object[].class)
            .getBody();
    for (Object notification : read)
      assertThat(((Map<?, ?>) notification).get("read")).isEqualTo(true);
    long noticeId = ((Number) ((Map<?, ?>) read[0]).get("id")).longValue();
    assertThat(
            request(HttpMethod.PUT, "/api/auth/preferences", Map.of("notifications", false))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    sessionCookie = ownerCookie;
    assertThat(
            request(HttpMethod.PUT, "/api/notifications/" + noticeId + "/read", null)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            request(HttpMethod.DELETE, "/api/tasks/" + taskId + "/comments/" + ownComment, null)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    request(
        HttpMethod.POST,
        "/api/tasks/" + taskId + "/comments",
        Map.of("message", "Muted recipient"));
    sessionCookie = memberCookie;
    assertThat(
            rest.exchange(
                    "/api/notifications",
                    HttpMethod.GET,
                    new HttpEntity<>(cookieHeaders()),
                    Object[].class)
                .getBody())
        .hasSize(2);
    sessionCookie = ownerCookie;
    assertThat(request(HttpMethod.DELETE, "/api/projects/" + projectId, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void archiveTrashRestoreRetrospectivesAndPreferencesPersistAndStayIsolated() {
    registerTestUser();
    long projectId =
        id(
            request(
                HttpMethod.POST,
                "/api/projects/templates",
                Map.of("name", "Lifecycle " + java.util.UUID.randomUUID(), "template", "Scrum")));
    var task = taskRepository.findByProjectId(projectId).get(0);
    long sprintId = task.getSprintId();
    long storyId = task.getStoryId();
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/auth/preferences",
                    Map.of(
                        "language",
                        "SV",
                        "theme",
                        "dark",
                        "notifications",
                        false,
                        "onboardingCompleted",
                        true,
                        "savedFilters",
                        "[{\"id\":\"qa\",\"name\":\"QA\",\"projectId\":" + projectId + "}]"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(request(HttpMethod.GET, "/api/auth/preferences", null).getBody())
        .containsEntry("language", "SV")
        .containsEntry("theme", "dark")
        .containsEntry("onboardingCompleted", true);
    assertThat(
            request(HttpMethod.PUT, "/api/auth/preferences", Map.of("language", "invalid"))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            request(HttpMethod.PUT, "/api/auth/preferences", Map.of("savedFilters", "not-json"))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    long note =
        id(
            request(
                HttpMethod.POST,
                "/api/projects/" + projectId + "/retrospective",
                Map.of("sprintId", sprintId, "category", "well", "message", "We worked together")));
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/projects/" + projectId + "/retrospective/" + note,
                    Map.of(
                        "sprintId", sprintId, "category", "actions", "message", "Review earlier"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    long otherProject =
        id(
            request(
                HttpMethod.POST,
                "/api/projects/templates",
                Map.of("name", "Other " + java.util.UUID.randomUUID(), "template", "Scrum")));
    long otherSprint = taskRepository.findByProjectId(otherProject).get(0).getSprintId();
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/projects/" + projectId + "/retrospective",
                    Map.of("sprintId", otherSprint, "category", "well", "message", "Cross project"))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/projects/" + projectId + "/retrospective/" + note,
                    Map.of(
                        "sprintId",
                        sprintId,
                        "category",
                        "actions",
                        "message",
                        "Stale",
                        "version",
                        999))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/projects/" + projectId + "/archive",
                    Map.of("archived", true))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/tasks",
                    Map.of("projectId", projectId, "title", "Archived write", "status", "To Do"))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            rest.exchange(
                    "/api/tasks?projectId=" + projectId,
                    HttpMethod.GET,
                    new HttpEntity<>(cookieHeaders()),
                    java.util.List.class)
                .getBody())
        .hasSize(3);
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/projects/" + projectId + "/archive",
                    Map.of("archived", false))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/workspace/trash/story/" + storyId,
                    Map.of("restore", false))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(taskRepository.findByProjectId(projectId)).isEmpty();
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/workspace/trash/task/" + task.getId(),
                    Map.of("restore", true))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/workspace/trash/story/" + storyId,
                    Map.of("restore", true))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(taskRepository.findByProjectId(projectId)).hasSize(3);
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/workspace/trash/sprint/" + sprintId,
                    Map.of("restore", false))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(taskRepository.findByProjectId(projectId)).isEmpty();
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/projects/" + projectId + "/retrospective/" + note,
                    Map.of("sprintId", sprintId, "category", "well", "message", "Hidden sprint"))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            request(
                    HttpMethod.DELETE,
                    "/api/projects/" + projectId + "/retrospective/" + note,
                    null)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/workspace/trash/sprint/" + sprintId,
                    Map.of("restore", true))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(taskRepository.findByProjectId(projectId)).hasSize(3);
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/workspace/trash/project/" + projectId,
                    Map.of("restore", false))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            request(HttpMethod.GET, "/api/projects/" + projectId + "/role", null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    Object[] trash =
        rest.exchange(
                "/api/workspace/trash",
                HttpMethod.GET,
                new HttpEntity<>(cookieHeaders()),
                Object[].class)
            .getBody();
    assertThat(trash).hasSize(1);
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/workspace/trash/project/" + projectId,
                    Map.of("restore", true))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(taskRepository.findByProjectId(projectId)).hasSize(3);
    assertThat(projectRows("retrospective_entry", projectId)).isEqualTo(1);
    assertThat(request(HttpMethod.DELETE, "/api/projects/" + projectId, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(projectRows("retrospective_entry", projectId)).isZero();
    assertThat(taskRepository.findByProjectId(otherProject)).hasSize(3);
    assertThat(request(HttpMethod.DELETE, "/api/projects/" + otherProject, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void sharedReportsArePublicSnapshotsThatCanBeRevokedAndExpire() {
    registerTestUser();
    String ownerCookie = sessionCookie;
    long projectId =
        id(
            request(
                HttpMethod.POST,
                "/api/projects/templates",
                Map.of("name", "Shared " + java.util.UUID.randomUUID(), "template", "Kanban")));
    ResponseEntity<Map> created =
        request(HttpMethod.POST, "/api/projects/" + projectId + "/reports", Map.of());
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
    String token = ((String) created.getBody().get("path")).split("=", 2)[1];
    long reportId = id(created);
    sessionCookie = null;
    var snapshot = request(HttpMethod.GET, "/api/shared-reports/" + token, null);
    assertThat(snapshot.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(snapshot.getBody())
        .containsEntry("taskCount", 3)
        .containsKey("velocityStoryPoints")
        .doesNotContainKeys("ownerEmail", "members", "comments", "password", "tokenHash");
    assertThat(request(HttpMethod.GET, "/api/shared-reports/invalid", null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    sessionCookie = ownerCookie;
    assertThat(
            request(HttpMethod.DELETE, "/api/projects/" + projectId + "/reports/" + reportId, null)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    sessionCookie = null;
    assertThat(request(HttpMethod.GET, "/api/shared-reports/" + token, null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    sessionCookie = ownerCookie;
    created = request(HttpMethod.POST, "/api/projects/" + projectId + "/reports", Map.of());
    String expiredToken = ((String) created.getBody().get("path")).split("=", 2)[1];
    jdbc.update(
        "update shared_report set expires_at = ? where id = ?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
        id(created));
    sessionCookie = null;
    assertThat(request(HttpMethod.GET, "/api/shared-reports/" + expiredToken, null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    sessionCookie = ownerCookie;
    assertThat(request(HttpMethod.DELETE, "/api/projects/" + projectId, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(projectRows("shared_report", projectId)).isZero();
  }

  private HttpHeaders cookieHeaders() {
    HttpHeaders headers = new HttpHeaders();
    if (sessionCookie != null) headers.set(HttpHeaders.COOKIE, sessionCookie);
    return headers;
  }

  private HttpHeaders csrfHeaders() {
    ResponseEntity<Map> response =
        rest.exchange(
            "/api/auth/csrf", HttpMethod.GET, new HttpEntity<>(cookieHeaders()), Map.class);
    String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    if (cookie != null) sessionCookie = cookie.split(";", 2)[0];
    Map body = response.getBody();
    HttpHeaders headers = cookieHeaders();
    headers.set((String) body.get("headerName"), (String) body.get("token"));
    return headers;
  }

  private ResponseEntity<Map> registerUser(String email) {
    String previousCookie = sessionCookie;
    sessionCookie = null;
    ResponseEntity<Map> response =
        rest.exchange(
            "/api/auth/register",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("email", email, "password", "secure-password", "displayName", "Test User"),
                csrfHeaders()),
            Map.class);
    sessionCookie = previousCookie;
    return response;
  }

  private ResponseEntity<Map> request(HttpMethod method, String path, Map<String, Object> body) {
    HttpHeaders headers =
        (method == HttpMethod.GET || method == HttpMethod.HEAD || method == HttpMethod.OPTIONS)
            ? cookieHeaders()
            : csrfHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (method == HttpMethod.PUT || method == HttpMethod.DELETE) {
      var matcher =
          java.util.regex.Pattern.compile(
                  "/api/(tasks|sprints|stories|projects)/(\\d+)(?:/.*|\\?.*)?")
              .matcher(path);
      var trash =
          java.util.regex.Pattern.compile("/api/workspace/trash/(task|sprint|story)/(\\d+)")
              .matcher(path);
      String kind = null;
      Long itemId = null;
      if (matcher.matches()) {
        kind = matcher.group(1);
        itemId = Long.valueOf(matcher.group(2));
      } else if (trash.matches()) {
        kind = trash.group(1) + "s";
        itemId = Long.valueOf(trash.group(2));
      }
      if (kind != null) {
        Object version =
            body != null && body.containsKey("version")
                ? body.get("version")
                : switch (kind) {
                  case "tasks" ->
                      taskRepository
                          .findById(itemId)
                          .map(com.sprintflowinsight.model.Task::getVersion)
                          .orElse(null);
                  case "projects" ->
                      jdbc
                          .queryForList(
                              "select version from projects where id = ?", Long.class, itemId)
                          .stream()
                          .findFirst()
                          .orElse(null);
                  case "sprints" ->
                      jdbc
                          .queryForList(
                              "select version from sprint where id = ?", Long.class, itemId)
                          .stream()
                          .findFirst()
                          .orElse(null);
                  default ->
                      jdbc
                          .queryForList(
                              "select version from user_stories where id = ?", Long.class, itemId)
                          .stream()
                          .findFirst()
                          .orElse(null);
                };
        if (version != null) headers.set("If-Match", "\"" + version + "\"");
      }
    }
    if (sessionCookie != null) headers.set(HttpHeaders.COOKIE, sessionCookie);
    ResponseEntity<Map> response =
        rest.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    if (cookie != null) sessionCookie = cookie.split(";", 2)[0];
    return response;
  }

  private long id(ResponseEntity<Map> response) {
    return ((Number) response.getBody().get("id")).longValue();
  }
}
