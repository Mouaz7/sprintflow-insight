package com.sprintflowinsight.support;

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

/**
 * End-to-end HTTP flows through the real server, real sessions and PostgreSQL. Each subclass
 * covers one feature area; this base provides the cookie-jar client and data helpers.
 */
@org.springframework.context.annotation.Import(com.sprintflowinsight.support.PostgresTestContainer.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.security.auth-rate-limit.enabled=false"
    })
public abstract class HttpFlowTest {

  @Autowired protected com.sprintflowinsight.story.UserStoryRepository storyRepository;

  protected String sessionCookie;
  protected String testEmail;

  protected void registerTestUser() {
    String email = "test-" + java.util.UUID.randomUUID() + "@example.com";
    testEmail = email;
    ResponseEntity<Map> response = registerUser(email);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    sessionCookie = cookie == null ? null : cookie.split(";", 2)[0];
  }

  @Autowired protected TestRestTemplate rest;

  @Autowired protected UserRepository userRepository;

  @Autowired protected NotificationService notificationService;

  @Autowired protected TaskRepository taskRepository;

  @Autowired protected TaskStatusHistoryRepository taskHistoryRepository;

  @Autowired protected ProjectRepository projectRepository;

  @Autowired protected ActivityEventRepository activityEventRepository;

  @Autowired protected org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired protected com.sprintflowinsight.project.ProjectDeletionService projectDeletion;
  @Autowired protected org.springframework.transaction.PlatformTransactionManager transactions;
  @Autowired protected com.sprintflowinsight.service.TaskService taskService;

  @Autowired protected com.sprintflowinsight.auth.UserEmailService userEmailService;

  protected long projectRows(String table, long projectId) {
    return jdbc.queryForObject(
        "select count(*) from " + table + " where project_id = ?", Long.class, projectId);
  }

  protected long populatedProject(String name) {
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

  protected HttpHeaders cookieHeaders() {
    HttpHeaders headers = new HttpHeaders();
    if (sessionCookie != null) headers.set(HttpHeaders.COOKIE, sessionCookie);
    return headers;
  }

  protected static String pdfText(byte[] pdf) {
    try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
      return new org.apache.pdfbox.text.PDFTextStripper().getText(document);
    } catch (java.io.IOException invalid) {
      throw new AssertionError("Export is not a readable PDF", invalid);
    }
  }

  protected HttpHeaders csrfHeaders() {
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

  protected ResponseEntity<Map> registerUser(String email) {
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

  protected ResponseEntity<Map> request(HttpMethod method, String path, Map<String, Object> body) {
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
          java.util.regex.Pattern.compile("/api/workspace/trash/(task|sprint|story|project)/(\\d+)")
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

  protected long id(ResponseEntity<Map> response) {
    return ((Number) response.getBody().get("id")).longValue();
  }
}
