package com.sprintflowinsight;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final org.slf4j.Logger SECURITY_LOG =
      org.slf4j.LoggerFactory.getLogger("security.events");

  @ExceptionHandler(org.springframework.mail.MailException.class)
  public ResponseEntity<Map<String, String>> handleMailFailure() {
    return error(
        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
        "The reset email could not be sent. Ask the administrator to check the email service and"
            + " try again.");
  }

  @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
  public ResponseEntity<Map<String, String>> handleAccessError(
      org.springframework.web.server.ResponseStatusException exception) {
    if (exception.getStatusCode().value() == 403) SECURITY_LOG.warn("Denied API operation.");
    return ResponseEntity.status(exception.getStatusCode())
        .body(
            Map.of(
                "message",
                exception.getReason() == null
                    ? "Access denied. Refresh the page and try again."
                    : exception.getReason()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidation(
      MethodArgumentNotValidException exception) {
    String message =
        exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getDefaultMessage())
            .orElse("Invalid input.");
    return error(org.springframework.http.HttpStatus.BAD_REQUEST, message);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, String>> handleConstraintViolation() {
    return error(
        org.springframework.http.HttpStatus.CONFLICT, "This record conflicts with existing data.");
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<Map<String, String>> handleConcurrentUpdate() {
    return error(
        org.springframework.http.HttpStatus.PRECONDITION_FAILED,
        "This item was changed by someone else. Refresh and try again.");
  }

  public static ResponseEntity<Map<String, String>> error(
      org.springframework.http.HttpStatus status, String message) {
    if (status == org.springframework.http.HttpStatus.FORBIDDEN)
      SECURITY_LOG.warn("Denied API operation.");
    return ResponseEntity.status(status).body(Map.of("message", message));
  }
}
