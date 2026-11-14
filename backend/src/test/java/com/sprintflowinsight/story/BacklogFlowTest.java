package com.sprintflowinsight.story;

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
import com.sprintflowinsight.*;
import com.sprintflowinsight.support.HttpFlowTest;

/** Backlog ordering, story planning and story/task movement. */
class BacklogFlowTest extends HttpFlowTest {
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
}
