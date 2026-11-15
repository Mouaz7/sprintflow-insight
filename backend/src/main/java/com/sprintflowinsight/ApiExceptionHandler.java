package com.sprintflowinsight;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.common.error.ErrorCode;
import com.sprintflowinsight.common.error.Problems;
import com.sprintflowinsight.common.error.Problems.FieldViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.mail.MailException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every failure into one RFC 9457 problem shape (see {@link Problems}). Spring MVC's own
 * exceptions (unreadable body, missing or mistyped parameters, unsupported media type, upload too
 * large, ...) are covered by {@link ResponseEntityExceptionHandler}; this class adds catalogue
 * codes, domain exceptions and a safe fallback that never leaks internals.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger SECURITY_LOG = LoggerFactory.getLogger("security.events");
  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(MailException.class)
  public ResponseEntity<ProblemDetail> handleMailFailure(
      MailException exception, WebRequest request) {
    LOG.warn("Mail delivery failed: {}", exception.getMessage());
    return respond(
        ErrorCode.SERVICE_UNAVAILABLE,
        "The reset email could not be sent. Ask the administrator to check the email service and"
            + " try again.",
        request);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> handleConstraintViolation(
      ConstraintViolationException exception, WebRequest request) {
    List<FieldViolation> violations =
        exception.getConstraintViolations().stream()
            .map(v -> new FieldViolation(lastNode(v.getPropertyPath().toString()), v.getMessage()))
            .sorted(java.util.Comparator.comparing(FieldViolation::field))
            .toList();
    return withInstance(Problems.validation(violations), request);
  }

  /** Database constraints that encode business rules, mapped to user-facing explanations. */
  private static final java.util.Map<String, String> CONSTRAINT_MESSAGES =
      java.util.Map.of(
          "sprint_one_active_project_idx",
          com.sprintflowinsight.workspace.project.ProjectMutationService.ACTIVE_SPRINT_MESSAGE,
          "story_points_allowed",
          "Story points must be 1, 2, 3, 5, or 8.",
          "task_points_range",
          "Story points must be between 0 and 1000.",
          "sprint_points_nonnegative",
          "Story points cannot be negative.");

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ProblemDetail> handleIntegrityViolation(
      DataIntegrityViolationException exception, WebRequest request) {
    String constraint = constraintName(exception);
    String message =
        constraint == null
            ? null
            : CONSTRAINT_MESSAGES.entrySet().stream()
                .filter(
                    entry -> constraint.toLowerCase(java.util.Locale.ROOT).contains(entry.getKey()))
                .map(java.util.Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    return respond(
        ErrorCode.CONFLICT,
        message == null ? "This record conflicts with existing data." : message,
        request);
  }

  /**
   * The database is unreachable, overloaded or timed out. That is temporary and not the client's
   * fault: answer 503 with a retry hint and never expose driver messages.
   */
  @ExceptionHandler({
    org.springframework.dao.DataAccessResourceFailureException.class,
    org.springframework.dao.TransientDataAccessException.class,
    org.springframework.transaction.CannotCreateTransactionException.class
  })
  public ResponseEntity<ProblemDetail> handleDatabaseUnavailable(
      Exception exception, WebRequest request) {
    LOG.warn("Database unavailable on {}: {}", path(request), exception.getClass().getSimpleName());
    ResponseEntity<ProblemDetail> response =
        respond(
            ErrorCode.SERVICE_UNAVAILABLE,
            com.sprintflowinsight.common.error.DatabaseFailures.DETAIL,
            request);
    return ResponseEntity.status(response.getStatusCode())
        .headers(response.getHeaders())
        .header(HttpHeaders.RETRY_AFTER, "5")
        .body(response.getBody());
  }

  /**
   * A competing transaction held a lock, caused a deadlock or changed the row first. The database
   * is healthy, so this is a 409 the client can resolve by reloading, never a 503. Spring picks
   * this over the {@link org.springframework.dao.TransientDataAccessException} handler because
   * {@link org.springframework.dao.ConcurrencyFailureException} is the closer match.
   */
  @ExceptionHandler(org.springframework.dao.ConcurrencyFailureException.class)
  public ResponseEntity<ProblemDetail> handleConcurrencyConflict(
      Exception exception, WebRequest request) {
    LOG.info("Concurrency conflict on {}: {}", path(request), exception.getClass().getSimpleName());
    return respond(
        ErrorCode.CONFLICT,
        com.sprintflowinsight.common.error.DatabaseFailures.CONFLICT_DETAIL,
        request);
  }

  @ExceptionHandler(java.util.NoSuchElementException.class)
  public ResponseEntity<ProblemDetail> handleMissing(WebRequest request) {
    return respond(ErrorCode.NOT_FOUND, "Resource not found.", request);
  }

  private static String constraintName(Throwable exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof org.hibernate.exception.ConstraintViolationException hibernate
          && hibernate.getConstraintName() != null) return hibernate.getConstraintName();
      if (cause.getMessage() != null)
        for (String key : CONSTRAINT_MESSAGES.keySet())
          if (cause.getMessage().toLowerCase(java.util.Locale.ROOT).contains(key)) return key;
    }
    return null;
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ProblemDetail> handleConcurrentUpdate(WebRequest request) {
    return respond(
        ErrorCode.PRECONDITION_FAILED,
        "This item was changed by someone else. Refresh and try again.",
        request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleIllegalArgument(
      IllegalArgumentException exception, WebRequest request) {
    LOG.debug("Rejected invalid argument", exception);
    return respond(ErrorCode.BAD_REQUEST, "The request contains an invalid value.", request);
  }

  /** Security failures raised inside MVC must not fall through to the 500 fallback. */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleAccessDenied(WebRequest request) {
    SECURITY_LOG.warn("Denied API operation.");
    return respond(ErrorCode.FORBIDDEN, "Access denied. Refresh the page and try again.", request);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ProblemDetail> handleAuthentication(WebRequest request) {
    return respond(ErrorCode.UNAUTHENTICATED, "Sign in to continue.", request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> handleUnexpected(Exception exception, WebRequest request)
      throws Exception {
    if (com.sprintflowinsight.common.error.DatabaseFailures.isConcurrencyConflict(exception))
      return handleConcurrencyConflict(exception, request);
    if (com.sprintflowinsight.common.error.DatabaseFailures.isDatabaseUnavailable(exception))
      return handleDatabaseUnavailable(exception, request);
    // Binding and proxies wrap deliberate client errors (e.g. a constructor rejecting a filter).
    for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
      if (cause instanceof ResponseStatusException clientError)
        return handleException(clientError, request);
      if (cause instanceof ConstraintViolationException violation)
        return handleConstraintViolation(violation, request);
    }
    String errorId = UUID.randomUUID().toString();
    LOG.error("Unhandled error {} on {}", errorId, path(request), exception);
    ProblemDetail problem =
        Problems.of(
            ErrorCode.INTERNAL_ERROR,
            "Something went wrong. Try again, and quote reference " + errorId + " if it persists.");
    problem.setProperty("errorId", errorId);
    return withInstance(problem, request);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldViolation> violations =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
            .toList();
    if (violations.isEmpty())
      violations =
          exception.getBindingResult().getGlobalErrors().stream()
              .map(error -> new FieldViolation(error.getObjectName(), error.getDefaultMessage()))
              .toList();
    return asObject(withInstance(Problems.validation(violations), request));
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldViolation> violations =
        exception.getParameterValidationResults().stream()
            .flatMap(
                result ->
                    result.getResolvableErrors().stream()
                        .map(
                            error ->
                                new FieldViolation(
                                    result.getMethodParameter().getParameterName(),
                                    error.getDefaultMessage())))
            .toList();
    return asObject(withInstance(Problems.validation(violations), request));
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception exception,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ErrorCode code = codeFor(exception, status);
    String detail = detailFor(exception, body, code);
    if (code == ErrorCode.FORBIDDEN) SECURITY_LOG.warn("Denied API operation.");
    ProblemDetail problem = Problems.of(code, detail);
    problem.setInstance(URI.create(path(request)));
    ResponseEntity<ProblemDetail> response = Problems.response(problem);
    return ResponseEntity.status(response.getStatusCode())
        .headers(headers)
        .contentType(response.getHeaders().getContentType())
        .body(problem);
  }

  /** Builds a problem response from a controller. Prefer throwing {@link ApiException}. */
  public static ResponseEntity<ProblemDetail> error(HttpStatus status, String message) {
    ErrorCode code = ErrorCode.forStatus(status);
    if (code == ErrorCode.FORBIDDEN) SECURITY_LOG.warn("Denied API operation.");
    return Problems.response(code, message);
  }

  private static ErrorCode codeFor(Exception exception, HttpStatusCode status) {
    if (exception instanceof ApiException api) return api.code();
    if (exception instanceof HttpMessageNotReadableException) return ErrorCode.MALFORMED_REQUEST;
    if (exception instanceof MissingServletRequestParameterException)
      return ErrorCode.MISSING_PARAMETER;
    if (exception instanceof TypeMismatchException) return ErrorCode.INVALID_PARAMETER;
    return ErrorCode.forStatus(status);
  }

  private static String detailFor(Exception exception, @Nullable Object body, ErrorCode code) {
    if (exception instanceof ResponseStatusException statusException) {
      String reason = statusException.getReason();
      if (reason != null && !reason.isBlank()) return reason;
      return code == ErrorCode.FORBIDDEN
          ? "Access denied. Refresh the page and try again."
          : code.title() + ".";
    }
    if (exception instanceof HttpMessageNotReadableException)
      return "The request body could not be read. Check that it is valid JSON with the expected"
          + " fields.";
    if (exception instanceof MissingServletRequestParameterException missing)
      return "Required parameter '" + missing.getParameterName() + "' is missing.";
    if (exception instanceof TypeMismatchException mismatch)
      return "Parameter '" + mismatch.getPropertyName() + "' has an invalid value.";
    if (exception instanceof MaxUploadSizeExceededException)
      return "File must be between 1 byte and 15 MB.";
    if (body instanceof ProblemDetail problem && problem.getDetail() != null)
      return problem.getDetail();
    return code.title() + ".";
  }

  private ResponseEntity<ProblemDetail> respond(ErrorCode code, String detail, WebRequest request) {
    return withInstance(Problems.of(code, detail), request);
  }

  private static ResponseEntity<ProblemDetail> withInstance(
      ProblemDetail problem, WebRequest request) {
    problem.setInstance(URI.create(path(request)));
    return Problems.response(problem);
  }

  @SuppressWarnings("unchecked")
  private static ResponseEntity<Object> asObject(ResponseEntity<ProblemDetail> response) {
    return (ResponseEntity<Object>) (ResponseEntity<?>) response;
  }

  private static String path(WebRequest request) {
    return request instanceof ServletWebRequest servlet
        ? servlet.getRequest().getRequestURI()
        : "about:blank";
  }

  private static String lastNode(String propertyPath) {
    int dot = propertyPath.lastIndexOf('.');
    return dot < 0 ? propertyPath : propertyPath.substring(dot + 1);
  }
}
