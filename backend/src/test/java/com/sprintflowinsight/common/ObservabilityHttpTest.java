package com.sprintflowinsight.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.support.HttpFlowTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(OutputCaptureExtension.class)
@Import(ObservabilityHttpTest.FailureFixture.class)
class ObservabilityHttpTest extends HttpFlowTest {
  @Value("${local.management.port}")
  int managementPort;

  @Autowired ObjectMapper json;

  @Test
  void restrictsPrometheusToTheManagementListenerAndCountsCommittedTasks() {
    assertThat(request(HttpMethod.GET, "/actuator/health", null).getBody())
        .containsExactlyEntriesOf(Map.of("status", "UP"));
    assertThat(request(HttpMethod.GET, "/actuator/prometheus", null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    registerTestUser();
    var project =
        request(HttpMethod.POST, "/api/projects", Map.of("name", "Observability fixture"));
    String metrics = "http://127.0.0.1:" + managementPort + "/actuator/prometheus";
    double before =
        sample(rest.getForEntity(metrics, String.class).getBody(), "sprintflow_tasks_total");
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/tasks",
                    Map.of("projectId", id(project), "title", "Observed task", "status", "To Do"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    org.awaitility.Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              var scrape = rest.getForEntity(metrics, String.class);
              assertThat(scrape.getStatusCode()).isEqualTo(HttpStatus.OK);
              assertThat(scrape.getBody())
                  .contains(
                      "http_server_requests_seconds_bucket",
                      "sprintflow_outbox_backlog",
                      "sprintflow_realtime_published_total");
              assertThat(sample(scrape.getBody(), "sprintflow_tasks_total")).isGreaterThan(before);
            });
  }

  @Test
  void theProblemReferenceAndTraceIdMatchTheStructuredServerLog(CapturedOutput output) {
    registerTestUser();
    var response =
        request(HttpMethod.GET, "/api/__observability_fixture/private@example.test", null);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    String traceId = (String) response.getBody().get("traceId");
    String errorId = (String) response.getBody().get("errorId");
    assertThat(traceId).matches("[0-9a-f]{32}");
    assertThat(errorId).isNotBlank();
    var entry =
        output
            .getOut()
            .lines()
            .filter(line -> line.startsWith("{") && line.contains(errorId))
            .map(json::readTree)
            .findFirst()
            .orElseThrow();
    assertThat(entry.path("traceId").asText()).isEqualTo(traceId);
    assertThat(entry.path("errorId").asText()).isEqualTo(errorId);
    assertThat(entry.toString()).doesNotContain("private", "fixture credential value");
  }

  private static double sample(String scrape, String name) {
    var metric =
        java.util.regex.Pattern.compile(
            "^" + java.util.regex.Pattern.quote(name) + "(?:\\{[^}]*\\})?\\s+([^ ]+)(?: .*)?$");
    return scrape
        .lines()
        .map(metric::matcher)
        .filter(java.util.regex.Matcher::matches)
        .mapToDouble(matcher -> Double.parseDouble(matcher.group(1)))
        .findFirst()
        .orElseThrow();
  }

  @RestController
  static class FailureFixture {
    @GetMapping("/api/__observability_fixture/{email}")
    String fail() {
      throw new IllegalStateException("fixture credential value");
    }
  }
}
