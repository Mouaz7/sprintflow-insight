package com.sprintflowinsight.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.support.HttpFlowTest;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Metric isolation and public shared reports. */
class MetricsFlowTest extends HttpFlowTest {
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
}
