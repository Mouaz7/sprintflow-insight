package com.sprintflowinsight.forecast;

import java.util.Arrays;

/**
 * Completed work per consecutive period, oldest first. A period is normally a week of item counts;
 * sprint velocity uses one period per sprint and story points as the unit.
 */
public final class ThroughputHistory {
  private final int[] counts;
  private final int periodDays;
  private final ThroughputUnit unit;

  private ThroughputHistory(int[] counts, int periodDays, ThroughputUnit unit) {
    if (periodDays <= 0) throw new IllegalArgumentException("periodDays must be positive");
    if (unit == null) throw new IllegalArgumentException("unit is required");
    for (int count : counts) {
      if (count < 0) throw new IllegalArgumentException("counts must not be negative");
    }
    this.counts = counts;
    this.periodDays = periodDays;
    this.unit = unit;
  }

  /** Weekly item throughput, oldest week first. */
  public static ThroughputHistory weekly(int... counts) {
    return of(7, ThroughputUnit.ITEMS, counts);
  }

  /** Story points completed per sprint of the given length, oldest sprint first. */
  public static ThroughputHistory sprintVelocity(int sprintDays, int... points) {
    return of(sprintDays, ThroughputUnit.STORY_POINTS, points);
  }

  public static ThroughputHistory of(int periodDays, ThroughputUnit unit, int... counts) {
    return new ThroughputHistory(counts.clone(), periodDays, unit);
  }

  public int size() {
    return counts.length;
  }

  public int count(int period) {
    return counts[period];
  }

  public int[] counts() {
    return counts.clone();
  }

  public int periodDays() {
    return periodDays;
  }

  public ThroughputUnit unit() {
    return unit;
  }

  /** Sum of the counts in periods {@code [from, to)}. */
  public int total(int from, int to) {
    int sum = 0;
    for (int i = from; i < to; i++) sum += counts[i];
    return sum;
  }

  /** The first {@code periods} periods, as known at that forecast origin. */
  public ThroughputHistory prefix(int periods) {
    return new ThroughputHistory(Arrays.copyOf(counts, periods), periodDays, unit);
  }

  /** Converts calendar days to (possibly fractional) periods. */
  public double periodsFor(long days) {
    return (double) days / periodDays;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ThroughputHistory that
        && periodDays == that.periodDays
        && unit == that.unit
        && Arrays.equals(counts, that.counts);
  }

  @Override
  public int hashCode() {
    return 31 * (31 * Arrays.hashCode(counts) + periodDays) + unit.hashCode();
  }

  @Override
  public String toString() {
    return "ThroughputHistory" + Arrays.toString(counts) + "/" + periodDays + "d " + unit;
  }
}
