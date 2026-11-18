package com.sprintflowinsight.common.error;

import org.springframework.web.server.ResponseStatusException;

/**
 * A client-facing failure with an explicit catalogue code. Extends {@link ResponseStatusException}
 * so existing status-based handling keeps working while the code is preserved.
 */
public class ApiException extends ResponseStatusException {
  private final ErrorCode code;

  public ApiException(ErrorCode code, String message) {
    super(code.status(), message);
    this.code = code;
  }

  public ErrorCode code() {
    return code;
  }

  /**
   * Adds a machine-readable extension member to the problem document, e.g. the task ids of a
   * dependency cycle. Values must be safe to show to the caller.
   */
  public ApiException with(String name, Object value) {
    getBody().setProperty(name, value);
    return this;
  }

  public static ApiException notFound(String message) {
    return new ApiException(ErrorCode.NOT_FOUND, message);
  }

  public static ApiException badRequest(String message) {
    return new ApiException(ErrorCode.BAD_REQUEST, message);
  }

  public static ApiException conflict(String message) {
    return new ApiException(ErrorCode.CONFLICT, message);
  }

  public static ApiException forbidden(String message) {
    return new ApiException(ErrorCode.FORBIDDEN, message);
  }
}
