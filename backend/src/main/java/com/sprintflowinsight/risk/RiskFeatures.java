package com.sprintflowinsight.risk;

import java.util.Arrays;

/**
 * Feature vector for one task at one point in time. All values are non-negative and finite.
 *
 * @param ageInColumnVsP85 age in the current column relative to the column's historical 85th
 *     percentile, see {@link #ageRatio(double, double[])}
 * @param timesBlocked times the task has been blocked
 * @param reassignments number of assignee changes
 * @param commentsLast7Days comments in the last seven days
 * @param storyPoints story points (0 when unestimated)
 * @param dependencyDepth longest chain of unfinished dependencies
 * @param remainingSprintDays days until sprint end, 0 when passed
 * @param columnWip tasks currently in the same column
 */
public record RiskFeatures(
    double ageInColumnVsP85,
    int timesBlocked,
    int reassignments,
    int commentsLast7Days,
    double storyPoints,
    int dependencyDepth,
    double remainingSprintDays,
    int columnWip) {

  /** Percentile of historical column ages used as the reference for {@link #ageRatio}. */
  public static final double AGE_PERCENTILE = 0.85;

  /** Lower bound of the reference age, so columns passed in minutes do not explode the ratio. */
  public static final double MIN_REFERENCE_HOURS = 1.0;

  public RiskFeatures {
    requireNonNegative("ageInColumnVsP85", ageInColumnVsP85);
    requireNonNegative("timesBlocked", timesBlocked);
    requireNonNegative("reassignments", reassignments);
    requireNonNegative("commentsLast7Days", commentsLast7Days);
    requireNonNegative("storyPoints", storyPoints);
    requireNonNegative("dependencyDepth", dependencyDepth);
    requireNonNegative("remainingSprintDays", remainingSprintDays);
    requireNonNegative("columnWip", columnWip);
  }

  /** Values in {@link RiskFeature} order. */
  public double[] toVector() {
    return new double[] {
      ageInColumnVsP85,
      timesBlocked,
      reassignments,
      commentsLast7Days,
      storyPoints,
      dependencyDepth,
      remainingSprintDays,
      columnWip
    };
  }

  /**
   * Age in the current column relative to the {@link #AGE_PERCENTILE} of the hours earlier tasks
   * spent in that column. The reference is at least {@link #MIN_REFERENCE_HOURS}. Without history
   * the ratio is neutral (1.0).
   */
  public static double ageRatio(double ageHours, double[] historicalColumnHours) {
    requireNonNegative("ageHours", ageHours);
    if (historicalColumnHours.length == 0) {
      return 1.0;
    }
    double reference = Math.max(MIN_REFERENCE_HOURS, percentile(historicalColumnHours, AGE_PERCENTILE));
    return ageHours / reference;
  }

  /**
   * Percentile with linear interpolation between order statistics (Hyndman–Fan type 7, the
   * default of R and NumPy).
   */
  public static double percentile(double[] values, double p) {
    if (values.length == 0) {
      throw new IllegalArgumentException("percentile of no values");
    }
    if (!(p >= 0 && p <= 1)) {
      throw new IllegalArgumentException("p must be in [0, 1]");
    }
    double[] sorted = values.clone();
    Arrays.sort(sorted);
    double position = p * (sorted.length - 1);
    int lower = (int) Math.floor(position);
    int upper = Math.min(lower + 1, sorted.length - 1);
    double fraction = position - lower;
    return sorted[lower] + fraction * (sorted[upper] - sorted[lower]);
  }

  private static void requireNonNegative(String name, double value) {
    if (!(value >= 0) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
  }
}
