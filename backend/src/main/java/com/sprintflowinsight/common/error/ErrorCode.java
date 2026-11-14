package com.sprintflowinsight.common.error;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Stable, documented error catalogue (RFC 9457). The {@link #type()} URI and {@link #code()} are
 * part of the public API contract: clients may branch on them, so existing values must never change
 * meaning.
 */
public enum ErrorCode {
  MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
  INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "Invalid parameter"),
  MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "Missing parameter"),
  BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad request"),
  UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
  NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
  NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Not acceptable"),
  CONFLICT(HttpStatus.CONFLICT, "Conflict"),
  GONE(HttpStatus.GONE, "Gone"),
  PRECONDITION_FAILED(HttpStatus.PRECONDITION_FAILED, "Stale version"),
  PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Payload too large"),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
  PRECONDITION_REQUIRED(HttpStatus.PRECONDITION_REQUIRED, "Version required"),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error"),
  SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable");

  public static final String TYPE_BASE = "https://sprintflow.dev/problems/";

  private final HttpStatus status;
  private final String title;

  ErrorCode(HttpStatus status, String title) {
    this.status = status;
    this.title = title;
  }

  public HttpStatus status() {
    return status;
  }

  public String title() {
    return title;
  }

  /** Machine-readable code, e.g. {@code VALIDATION_FAILED}. */
  public String code() {
    return name();
  }

  /** Problem type URI, e.g. {@code https://sprintflow.dev/problems/validation-failed}. */
  public URI type() {
    return URI.create(TYPE_BASE + name().toLowerCase(Locale.ROOT).replace('_', '-'));
  }

  /** The generic code for a status that was raised without an explicit code. */
  public static ErrorCode forStatus(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> BAD_REQUEST;
      case 401 -> UNAUTHENTICATED;
      case 403 -> FORBIDDEN;
      case 404 -> NOT_FOUND;
      case 405 -> METHOD_NOT_ALLOWED;
      case 406 -> NOT_ACCEPTABLE;
      case 409 -> CONFLICT;
      case 410 -> GONE;
      case 412 -> PRECONDITION_FAILED;
      case 413 -> PAYLOAD_TOO_LARGE;
      case 415 -> UNSUPPORTED_MEDIA_TYPE;
      case 428 -> PRECONDITION_REQUIRED;
      case 429 -> RATE_LIMITED;
      case 503 -> SERVICE_UNAVAILABLE;
      default -> status.is4xxClientError() ? BAD_REQUEST : INTERNAL_ERROR;
    };
  }
}
