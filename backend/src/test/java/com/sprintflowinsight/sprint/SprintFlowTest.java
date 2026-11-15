package com.sprintflowinsight.sprint;

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

/** Sprint lifecycle, the single-active rule and completed-sprint history. */
class SprintFlowTest extends HttpFlowTest {
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
}
