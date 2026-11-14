package com.sprintflowinsight.lifecycle;

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

/** Archive, recycle bin, restore and purge. */
class RecycleBinFlowTest extends HttpFlowTest {
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
}
