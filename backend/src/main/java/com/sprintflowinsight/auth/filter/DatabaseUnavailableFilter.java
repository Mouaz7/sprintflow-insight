package com.sprintflowinsight.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprintflowinsight.auth.SecurityConfiguration;
import com.sprintflowinsight.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sessions live in PostgreSQL, so during a database outage the failure can surface outside Spring
 * MVC: when the session filter loads the session, or saves it while the response is committed. This
 * filter wraps the session filter and turns such failures into the same retryable 503 problem the
 * MVC handler produces, instead of a container error page.
 */
@Component
@Order(SessionRepositoryFilter.DEFAULT_ORDER - 10)
public class DatabaseUnavailableFilter extends OncePerRequestFilter implements Ordered {
  private static final Logger LOG = LoggerFactory.getLogger(DatabaseUnavailableFilter.class);
  public static final String DETAIL = "The database is temporarily unavailable. Try again shortly.";

  private final ObjectMapper json;

  public DatabaseUnavailableFilter(ObjectMapper json) {
    this.json = json;
  }

  @Override
  public int getOrder() {
    return SessionRepositoryFilter.DEFAULT_ORDER - 10;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      chain.doFilter(request, response);
    } catch (RuntimeException | ServletException | IOException failure) {
      if (!isDatabaseUnavailable(failure) || response.isCommitted()) throw failure;
      LOG.warn("Database unavailable on {}: {}", request.getRequestURI(), rootName(failure));
      response.resetBuffer();
      response.setHeader(HttpHeaders.CONTENT_LENGTH, null);
      response.setHeader(HttpHeaders.RETRY_AFTER, "5");
      SecurityConfiguration.writeProblem(
          json, request, response, ErrorCode.SERVICE_UNAVAILABLE, DETAIL);
    }
  }

  /**
   * True when any cause is a connection-level database failure: Spring's resource/transient
   * exceptions, a transaction that could not start, or a JDBC error in SQLState class 08
   * (connection exception). A failed rollback hides the original error inside {@link
   * TransactionSystemException}, so that is inspected too.
   */
  public static boolean isDatabaseUnavailable(Throwable failure) {
    return com.sprintflowinsight.common.error.DatabaseFailures.isDatabaseUnavailable(failure);
  }

  private static String rootName(Throwable failure) {
    Throwable root = failure;
    while (root.getCause() != null) root = root.getCause();
    return root.getClass().getSimpleName();
  }
}
