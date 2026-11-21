package com.sprintflowinsight.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.common.SecureTokens;
import com.sprintflowinsight.support.HttpFlowTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class PublicApiIntegrationTest extends HttpFlowTest {
  private static final String TOKENS = "/api/auth/tokens";
  private static final String PROJECTS = "/api/v1/projects";

  @Test
  void authenticatesOnlyScopedBearerTokensAndTracksUseAndRevocation() {
    registerTestUser();
    long project =
        id(request(HttpMethod.POST, "/api/projects", Map.of("name", "Public API fixture")));
    assertThat(request(HttpMethod.GET, PROJECTS, null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    var token = issue("PROJECT_READ", "TASK_READ");
    assertThat(
            jdbc.queryForObject(
                "select token_digest from personal_access_token where id=?",
                String.class,
                token.id()))
        .isEqualTo(SecureTokens.sha256(token.raw()));
    var page = bearer(token, HttpMethod.GET, PROJECTS + "?size=1", null);
    assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(page.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();
    assertThat((List<?>) page.getBody().get("items")).hasSize(1);
    assertThat(
            jdbc.queryForObject(
                "select last_used_at is not null from personal_access_token where id=?",
                Boolean.class,
                token.id()))
        .isTrue();
    String tasks = PROJECTS + "/" + project + "/tasks";
    assertThat(
            bearer(token, HttpMethod.POST, tasks, Map.of("title", "Forbidden write"))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    var writable = issue("TASK_WRITE");
    var created =
        bearer(writable, HttpMethod.POST, tasks, Map.of("title", "Token task", "storyPoints", 3));
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getHeaders().getLocation().toString()).startsWith(tasks + "/");
    assertThat(bearer(token, HttpMethod.GET, tasks + "/" + id(created), null).getBody())
        .containsEntry("title", "Token task")
        .doesNotContainKey("assigneeEmail");

    String ownerSession = sessionCookie;
    registerTestUser();
    long foreignProject =
        id(request(HttpMethod.POST, "/api/projects", Map.of("name", "Other API fixture")));
    assertThat(bearer(token, HttpMethod.GET, PROJECTS + "/" + foreignProject, null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(request(HttpMethod.DELETE, TOKENS + "/" + token.id(), null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    sessionCookie = ownerSession;
    assertThat(request(HttpMethod.DELETE, TOKENS + "/" + token.id(), null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(bearer(token, HttpMethod.GET, PROJECTS, null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    jdbc.update(
        """
        update personal_access_token set issued_at=clock_timestamp()-interval '2 days',
          expires_at=clock_timestamp()-interval '1 day' where id=?
        """,
        writable.id());
    assertThat(bearer(writable, HttpMethod.POST, tasks, Map.of("title", "Expired")).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void enforcesProjectRolesAndArchivalEvenWhenTheBearerTokenHasWriteScope() {
    registerTestUser();
    String ownerSession = sessionCookie;
    long project =
        id(request(HttpMethod.POST, "/api/projects", Map.of("name", "Public API roles")));
    String tasks = PROJECTS + "/" + project + "/tasks";
    var ownerToken = issue("TASK_READ", "TASK_WRITE");
    for (String role : List.of("Admin", "Member", "Viewer")) {
      registerTestUser();
      String memberSession = sessionCookie;
      sessionCookie = ownerSession;
      assertThat(
              request(
                      HttpMethod.POST,
                      "/api/projects/" + project + "/members",
                      Map.of("email", testEmail, "role", role))
                  .getStatusCode())
          .isEqualTo(HttpStatus.OK);
      sessionCookie = memberSession;
      var scoped = issue("TASK_READ", "TASK_WRITE");
      assertThat(bearer(scoped, HttpMethod.GET, tasks, null).getStatusCode())
          .isEqualTo(HttpStatus.OK);
      assertThat(
              bearer(scoped, HttpMethod.POST, tasks, Map.of("title", role + " task"))
                  .getStatusCode())
          .isEqualTo("Viewer".equals(role) ? HttpStatus.FORBIDDEN : HttpStatus.CREATED);
    }
    sessionCookie = ownerSession;
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/projects/" + project + "/archive",
                    Map.of("archived", true))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(bearer(ownerToken, HttpMethod.GET, tasks, null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            bearer(ownerToken, HttpMethod.POST, tasks, Map.of("title", "Archived write"))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void generatesAnOpenApiDocumentLimitedToTheVersionedSurface() {
    var result = request(HttpMethod.GET, "/api/v1/openapi", null);
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    Map<?, ?> paths = (Map<?, ?>) result.getBody().get("paths");
    assertThat(paths.keySet().stream().map(Object::toString).toList())
        .containsExactlyInAnyOrder(
            "/api/v1/projects", "/api/v1/projects/{projectId}",
            "/api/v1/projects/{projectId}/tasks", "/api/v1/projects/{projectId}/tasks/{taskId}");
  }

  private record Issued(long id, String raw) {
    @Override
    public String toString() {
      return "TestAccessToken[id=" + id + "]";
    }
  }

  private Issued issue(String... scopes) {
    var response =
        request(
            HttpMethod.POST,
            TOKENS,
            Map.of("label", "CI", "scopes", List.of(scopes), "expiresInDays", 30));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    var metadata = (Map<?, ?>) response.getBody().get("metadata");
    return new Issued(
        ((Number) metadata.get("id")).longValue(), (String) response.getBody().get("token"));
  }

  private ResponseEntity<Map> bearer(
      Issued token, HttpMethod method, String path, Map<String, Object> body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token.raw());
    headers.setContentType(MediaType.APPLICATION_JSON);
    return rest.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
  }
}
