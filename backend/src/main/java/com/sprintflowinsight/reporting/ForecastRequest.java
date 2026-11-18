package com.sprintflowinsight.reporting;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Optional query parameters of {@code GET /api/projects/{id}/forecast}. Every field may be omitted:
 * the basis defaults to throughput, the remaining work to the project's unfinished tasks, and the
 * target to the active sprint's end date and unfinished sprint work (or four periods).
 *
 * @param remaining remaining work in the basis unit (items or story points)
 * @param targetItems work that must be done by {@code targetDate} for the target probability
 * @param targetDate UTC calendar date; today allows zero additional calendar days
 * @param epicId selected project epic's remaining tasks; capacity history stays project-wide
 */
public record ForecastRequest(
    ForecastBasis basis,
    @Min(0) @Max(ForecastRequest.MAX_WORK) Integer remaining,
    @Min(0) @Max(ForecastRequest.MAX_WORK) Integer targetItems,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate,
    @Positive Long epicId) {
  /** Maximum planning quantity accepted by the API; automatic amounts are never truncated. */
  public static final int MAX_WORK = 10_000;

  public ForecastBasis basisOrDefault() {
    return basis == null ? ForecastBasis.THROUGHPUT : basis;
  }
}
