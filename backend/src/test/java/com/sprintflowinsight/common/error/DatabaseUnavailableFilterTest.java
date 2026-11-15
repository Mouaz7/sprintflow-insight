package com.sprintflowinsight.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.ObjectMapper;
import com.sprintflowinsight.auth.filter.DatabaseUnavailableFilter;
import jakarta.servlet.ServletException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.TransactionSystemException;

class DatabaseUnavailableFilterTest {
  @Test
  void replacesBufferedSuccessWithRetryableProblemAndKeepsSecurityHeaders() throws Exception {
    var json = new ObjectMapper();
    var request = new MockHttpServletRequest("GET", "/api/projects");
    var response = new MockHttpServletResponse();
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Content-Length", "2");
    new DatabaseUnavailableFilter(json)
        .doFilter(
            request,
            response,
            (req, res) -> {
              res.getOutputStream().write("[]".getBytes(java.nio.charset.StandardCharsets.UTF_8));
              throw new ServletException(
                  new DataAccessResourceFailureException("secret-driver-text"));
            });
    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getHeader("Retry-After")).isEqualTo("5");
    assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(response.getHeader("Content-Length"))
        .as("length describes the problem body, not the discarded success body")
        .isEqualTo(Integer.toString(response.getContentAsByteArray().length));
    assertThat(response.getContentType()).startsWith("application/problem+json");
    var problem = json.readTree(response.getContentAsString());
    assertThat(problem.path("code").asText()).isEqualTo("SERVICE_UNAVAILABLE");
    assertThat(problem.path("instance").asText()).isEqualTo("/api/projects");
    assertThat(response.getContentAsString()).doesNotContain("secret-driver-text");
  }

  @Test
  void unrelatedFailuresAndAlreadyCommittedResponsesPropagate() {
    var filter = new DatabaseUnavailableFilter(new ObjectMapper());
    var request = new MockHttpServletRequest("GET", "/api/projects");
    var dataError = new DataIntegrityViolationException("duplicate");
    assertThatThrownBy(
            () ->
                filter.doFilter(
                    request,
                    new MockHttpServletResponse(),
                    (req, res) -> {
                      throw dataError;
                    }))
        .isSameAs(dataError);

    var committed = new MockHttpServletResponse();
    committed.setCommitted(true);
    var outage = new DataAccessResourceFailureException("offline");
    assertThatThrownBy(
            () ->
                filter.doFilter(
                    new MockHttpServletRequest(),
                    committed,
                    (req, res) -> {
                      throw outage;
                    }))
        .isSameAs(outage);
    assertThat(committed.getHeader("Retry-After")).isNull();
  }

  @Test
  void recognisesConnectionFailuresHoweverDeeplyWrapped() {
    var refused = new SQLException("The connection attempt failed.", "08001");
    assertThat(
            DatabaseUnavailableFilter.isDatabaseUnavailable(
                new JpaSystemException(new RuntimeException(refused))))
        .isTrue();

    var rollbackFailed = new TransactionSystemException("Unable to rollback");
    rollbackFailed.initApplicationException(new RuntimeException(refused));
    assertThat(DatabaseUnavailableFilter.isDatabaseUnavailable(rollbackFailed)).isTrue();

    var rollbackOnDeadConnection =
        new JpaSystemException(
            new RuntimeException("Unable to rollback", new SQLException("Connection is closed")));
    assertThat(DatabaseUnavailableFilter.isDatabaseUnavailable(rollbackOnDeadConnection)).isTrue();
  }

  @Test
  void clientAndDataErrorsAreNotMistakenForAnOutage() {
    assertThat(
            DatabaseUnavailableFilter.isDatabaseUnavailable(
                new DataIntegrityViolationException("duplicate")))
        .isFalse();
    assertThat(DatabaseUnavailableFilter.isDatabaseUnavailable(new SQLException("syntax", "42601")))
        .isFalse();
    assertThat(DatabaseUnavailableFilter.isDatabaseUnavailable(new IllegalStateException()))
        .isFalse();
  }
}
