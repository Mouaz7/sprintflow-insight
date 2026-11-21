package com.sprintflowinsight.common.error;

import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Builds RFC 9457 problem documents in one consistent shape:
 *
 * <pre>
 * { "type", "title", "status", "detail", "instance", "code", "message", ["errors"], ["errorId"] }
 * </pre>
 *
 * {@code message} mirrors {@code detail} so older clients that read {@code message} keep working.
 */
public final class Problems {
  private Problems() {}

  public static ProblemDetail of(ErrorCode code, String detail) {
    String text = detail == null || detail.isBlank() ? code.title() + "." : detail;
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), text);
    problem.setType(code.type());
    problem.setTitle(code.title());
    problem.setProperty("code", code.code());
    problem.setProperty("message", text);
    String traceId = MDC.get("traceId");
    if (traceId != null && traceId.matches("[0-9a-f]{32}")) problem.setProperty("traceId", traceId);
    return problem;
  }

  /** Validation problem listing each rejected field. The first message becomes the detail. */
  public static ProblemDetail validation(List<FieldViolation> violations) {
    String detail = violations.isEmpty() ? "Invalid input." : violations.get(0).message();
    ProblemDetail problem = of(ErrorCode.VALIDATION_FAILED, detail);
    problem.setProperty("errors", violations);
    return problem;
  }

  public static ResponseEntity<ProblemDetail> response(ErrorCode code, String detail) {
    return response(of(code, detail));
  }

  public static ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
    return ResponseEntity.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  /** Plain map form for writers outside Spring MVC (security entry points, servlet filters). */
  public static Map<String, Object> asMap(ProblemDetail problem) {
    var body = new java.util.LinkedHashMap<String, Object>();
    body.put("type", problem.getType().toString());
    body.put("title", problem.getTitle());
    body.put("status", problem.getStatus());
    body.put("detail", problem.getDetail());
    if (problem.getInstance() != null) body.put("instance", problem.getInstance().toString());
    if (problem.getProperties() != null) body.putAll(problem.getProperties());
    return body;
  }

  public record FieldViolation(String field, String message) {}
}
