package com.sprintflowinsight.flow;

/**
 * Little's law, {@code L = λ·W}, used as a data-quality indicator.
 *
 * <p>For a stable system that conserves flow, the time-averaged work in progress {@code L} equals
 * the completion rate {@code λ} times the mean time in the system {@code W}. A large mismatch over
 * a window means the status history does not describe the work consistently: missing or
 * back-dated transitions, work abandoned in an active column, many reopened items, or a window too
 * short for the system to be near steady state.
 *
 * @param status the verdict
 * @param averageWip measured time-averaged WIP, {@code L}
 * @param throughputPerDay completions per day, {@code λ}
 * @param averageCycleTimeDays mean cycle time in days, {@code W}
 * @param expectedWip {@code λ·W}
 * @param ratio {@code L / (λ·W)}; {@code null} when {@code λ·W} is zero
 */
public record LittlesLaw(
    Status status,
    double averageWip,
    double throughputPerDay,
    double averageCycleTimeDays,
    double expectedWip,
    Double ratio) {

  /** Default relative tolerance: the ratio must lie within [1/1.25, 1.25]. */
  public static final double DEFAULT_TOLERANCE = 0.25;

  /** Fewest completions with a cycle time before a verdict is given. */
  public static final int MIN_COMPLETIONS = 5;

  public enum Status {
    CONSISTENT,
    INCONSISTENT,
    INSUFFICIENT_DATA
  }

  /**
   * Compares measured WIP with {@code λ·W}.
   *
   * @param completions number of completions behind {@code throughputPerDay} and {@code
   *     averageCycleTimeDays}
   * @param tolerance relative tolerance {@code t > 0}; consistent iff {@code 1/(1+t) ≤ ratio ≤ 1+t}
   */
  public static LittlesLaw check(
      double averageWip,
      double throughputPerDay,
      double averageCycleTimeDays,
      int completions,
      double tolerance) {
    if (!(tolerance > 0)) throw new IllegalArgumentException("tolerance must be positive");
    if (!finiteNonNegative(averageWip)
        || !finiteNonNegative(throughputPerDay)
        || !finiteNonNegative(averageCycleTimeDays))
      throw new IllegalArgumentException("inputs must be finite and non-negative");
    double expected = throughputPerDay * averageCycleTimeDays;
    Double ratio = expected > 0 ? averageWip / expected : null;
    Status status;
    if (completions < MIN_COMPLETIONS) status = Status.INSUFFICIENT_DATA;
    else if (ratio == null) status = averageWip > 0 ? Status.INCONSISTENT : Status.CONSISTENT;
    else
      status =
          ratio <= 1 + tolerance && ratio * (1 + tolerance) >= 1
              ? Status.CONSISTENT
              : Status.INCONSISTENT;
    return new LittlesLaw(status, averageWip, throughputPerDay, averageCycleTimeDays, expected, ratio);
  }

  private static boolean finiteNonNegative(double value) {
    return Double.isFinite(value) && value >= 0;
  }
}
