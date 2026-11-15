package com.sprintflowinsight.task;

import com.sprintflowinsight.support.TestFiles;
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

/** Task editing, versions, history, exports and attachments. */
class TaskFlowTest extends HttpFlowTest {
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
    assertThat(pdfText(pdf.getBody()))
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
    assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(pdf.getHeaders().getContentType().toString()).startsWith("application/pdf");
    assertThat(new String(pdf.getBody(), 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    assertThat(pdfText(pdf.getBody())).contains("DELIVERY SUMMARY", "Overdue upload task");
  }
}
