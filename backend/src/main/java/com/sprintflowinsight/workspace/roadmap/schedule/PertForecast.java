package com.sprintflowinsight.workspace.roadmap.schedule;

import java.time.LocalDate;

/**
 * The probability that a network of PERT-estimated tasks is finished by a date.
 *
 * <p>Classic PERT treats the length of the critical path as normally distributed (central limit
 * theorem over the tasks on the path) with the path's summed mean and variance. Parallel chains
 * that are nearly critical are ignored, so the probability is optimistic when several chains have
 * almost the same length ("merge bias"); ADR 0019 records this limitation.
 *
 * @param start first working day of the remaining work
 * @param expectedDays expected remaining working days
 * @param standardDeviationDays standard deviation of the remaining working days
 * @param expectedFinish the working day on which the expected work finishes
 * @param probability {@code P(finish <= deadline)}, or {@code null} without a deadline
 */
public record PertForecast(
    LocalDate start,
    double expectedDays,
    double standardDeviationDays,
    LocalDate expectedFinish,
    Double probability) {

  public static PertForecast of(CriticalPath path, LocalDate start, LocalDate deadline) {
    double mean = path.duration();
    double deviation = path.standardDeviation();
    Double probability =
        deadline == null
            ? null
            : deadline.isBefore(start)
                ? 0d
                : probabilityWithin(mean, deviation, WorkingDays.between(start, deadline));
    return new PertForecast(start, mean, deviation, WorkingDays.finish(start, mean), probability);
  }

  /**
   * {@code P(T <= available)} for {@code T ~ N(mean, deviation^2)}. A deterministic network ({@code
   * deviation == 0}) is certain to finish on time exactly when its mean fits.
   */
  public static double probabilityWithin(double mean, double deviation, double available) {
    if (deviation <= CriticalPath.EPSILON) return mean <= available + CriticalPath.EPSILON ? 1 : 0;
    return StandardNormal.cdf((available - mean) / deviation);
  }
}
