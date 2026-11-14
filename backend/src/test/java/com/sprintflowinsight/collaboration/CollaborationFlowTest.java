package com.sprintflowinsight.collaboration;

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

/** Comments, notifications, templates, checklists and activity moderation. */
class CollaborationFlowTest extends HttpFlowTest {
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
}
