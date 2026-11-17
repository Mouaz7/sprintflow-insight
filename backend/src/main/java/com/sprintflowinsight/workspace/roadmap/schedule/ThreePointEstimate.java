package com.sprintflowinsight.workspace.roadmap.schedule;

/**
 * A PERT three-point estimate in working days. The duration is modelled as a beta distribution
 * whose mean and variance follow the classic PERT approximations: {@code (o + 4m + p) / 6} and
 * {@code ((p - o) / 6)^2}.
 *
 * @param optimistic shortest plausible duration
 * @param mostLikely the mode
 * @param pessimistic longest plausible duration
 */
public record ThreePointEstimate(double optimistic, double mostLikely, double pessimistic) {
  /** Largest accepted estimate, matching the database constraint. */
  public static final double MAX_DAYS = 1000;

  public ThreePointEstimate {
    if (!Double.isFinite(optimistic) || !Double.isFinite(mostLikely) || !Double.isFinite(pessimistic))
      throw new IllegalArgumentException("Estimates must be finite numbers");
    if (optimistic < 0 || optimistic > mostLikely || mostLikely > pessimistic || pessimistic > MAX_DAYS)
      throw new IllegalArgumentException(
          "Estimates must satisfy 0 <= optimistic <= most likely <= pessimistic <= " + MAX_DAYS);
  }

  /** True when the three values are ordered and within range. */
  public static boolean valid(double optimistic, double mostLikely, double pessimistic) {
    return Double.isFinite(optimistic)
        && Double.isFinite(mostLikely)
        && Double.isFinite(pessimistic)
        && optimistic >= 0
        && optimistic <= mostLikely
        && mostLikely <= pessimistic
        && pessimistic <= MAX_DAYS;
  }

  public double mean() {
    return (optimistic + 4 * mostLikely + pessimistic) / 6;
  }

  public double variance() {
    double spread = (pessimistic - optimistic) / 6;
    return spread * spread;
  }

  public double standardDeviation() {
    return (pessimistic - optimistic) / 6;
  }
}
