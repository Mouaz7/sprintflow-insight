# 0001 – RFC 9457 problem details with a stable error catalogue

**Status:** Accepted (phase A1)

## Context
Errors had three shapes: `{"message"}` maps, empty bodies (`ResponseEntity.notFound()`), and Spring's default error page. Clients could only react by matching English text. Some programming errors (`RuntimeException("Task not found")`) surfaced as HTTP 500, and data-binding wrappers turned deliberate 400s into 500s.

## Decision
- Every failure is `application/problem+json` (RFC 9457) with `type`, `title`, `status`, `detail`, `instance`, a machine-readable `code`, and `message` (equal to `detail`, kept for older clients).
- `ErrorCode` is the catalogue. Its `type` URI is `https://sprintflow.dev/problems/<kebab-case code>`. Codes are append-only, and an existing code never changes meaning.
- `ApiExceptionHandler` extends `ResponseEntityExceptionHandler`, so every Spring MVC exception is covered. Domain code throws `ApiException(code, message)`.
- Validation failures list every rejected field in `errors`. Unexpected failures log an `errorId` and return it, never the exception text.
- Database constraints that encode business rules map to readable messages (for example `sprint_one_active_project_idx`).

## Consequences
- Clients branch on `code`; texts can be translated or rephrased freely.
- `OpenApiContractTest` fails if the documented code enum and `ErrorCode` drift apart.
- Filters outside MVC (security entry points, rate limiting) use the same shape through `SecurityConfiguration.writeProblem`.
