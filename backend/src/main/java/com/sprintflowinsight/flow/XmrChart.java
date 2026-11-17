package com.sprintflowinsight.flow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * An XmR (individuals and moving range) process-behaviour chart, after Wheeler.
 *
 * <p>The centre line is the mean {@code X̄}; the moving ranges are {@code |xᵢ − xᵢ₋₁|} with mean
 * {@code MR̄}. Natural process limits are {@code X̄ ± 2.66·MR̄} (three sigma, with {@code σ̂ =
 * MR̄/1.128}) and the upper range limit is {@code 3.268·MR̄}. The limits are computed from all
 * values passed in.
 *
 * @param mean centre line
 * @param movingRangeMean mean moving range
 * @param upperLimit upper natural process limit
 * @param lowerLimit lower natural process limit; may be negative, callers decide how to display it
 * @param upperRangeLimit upper limit for a single moving range
 * @param points one per input value, in input order
 */
public record XmrChart(
    double mean,
    double movingRangeMean,
    double upperLimit,
    double lowerLimit,
    double upperRangeLimit,
    List<Point> points) {

  /** Scaling constant for natural process limits: 3 / d₂ with d₂ = 1.128 for n = 2. */
  public static final double LIMIT_FACTOR = 2.66;

  /** Upper range limit factor D₄ for n = 2. */
  public static final double RANGE_FACTOR = 3.268;

  /** Fewest values for which a chart is produced. */
  public static final int MIN_POINTS = 6;

  /** Length of the run on one side of the centre line that signals a shift. */
  public static final int RUN_LENGTH = 8;

  /** Detection rules. A point can raise several. */
  public enum Signal {
    /** Rule 1: the value lies outside the natural process limits. */
    OUTSIDE_LIMITS,
    /**
     * Rule 2: at least three of four successive values lie on the same side, beyond the midpoint
     * between the centre line and that limit (more than 1.5σ̂ from the mean).
     */
    THREE_OF_FOUR_BEYOND_HALF,
    /** Rule 3: eight or more successive values lie strictly on the same side of the centre line. */
    RUN_OF_EIGHT,
    /** Rule 4: the moving range ending at this value exceeds the upper range limit. */
    LARGE_MOVING_RANGE
  }

  /**
   * @param value the observation
   * @param movingRange {@code |value − previous|}, {@code null} for the first point
   * @param signals the rules this point takes part in
   */
  public record Point(double value, Double movingRange, Set<Signal> signals) {
    public Point {
      EnumSet<Signal> copy = EnumSet.noneOf(Signal.class);
      copy.addAll(signals);
      signals = Collections.unmodifiableSet(copy);
    }
  }

  public XmrChart {
    points = List.copyOf(points);
  }

  /**
   * The chart for {@code values} in time order, or empty when there are fewer than {@link
   * #MIN_POINTS}.
   *
   * @throws IllegalArgumentException for a non-finite value
   */
  public static Optional<XmrChart> of(List<Double> values) {
    int n = values.size();
    if (n < MIN_POINTS) return Optional.empty();
    double[] x = new double[n];
    for (int i = 0; i < n; i++) {
      Double value = values.get(i);
      if (value == null || !Double.isFinite(value))
        throw new IllegalArgumentException("values must be finite");
      x[i] = value;
    }
    double mean = 0;
    for (double value : x) mean += value;
    mean /= n;
    double[] ranges = new double[n];
    double rangeSum = 0;
    for (int i = 1; i < n; i++) {
      ranges[i] = Math.abs(x[i] - x[i - 1]);
      rangeSum += ranges[i];
    }
    double mrBar = rangeSum / (n - 1);
    double spread = LIMIT_FACTOR * mrBar;
    double upper = mean + spread;
    double lower = mean - spread;
    double rangeLimit = RANGE_FACTOR * mrBar;
    double half = spread / 2;

    List<EnumSet<Signal>> signals = new ArrayList<>(n);
    for (int i = 0; i < n; i++) signals.add(EnumSet.noneOf(Signal.class));
    for (int i = 0; i < n; i++) {
      if (x[i] > upper || x[i] < lower) signals.get(i).add(Signal.OUTSIDE_LIMITS);
      if (i > 0 && ranges[i] > rangeLimit) signals.get(i).add(Signal.LARGE_MOVING_RANGE);
    }
    for (int side = -1; side <= 1; side += 2) {
      // Rule 2: windows of four with at least three values beyond the half-way line.
      for (int start = 0; start + 4 <= n; start++) {
        int beyond = 0;
        for (int i = start; i < start + 4; i++) if (side * (x[i] - mean) > half) beyond++;
        if (beyond >= 3)
          for (int i = start; i < start + 4; i++)
            if (side * (x[i] - mean) > half) signals.get(i).add(Signal.THREE_OF_FOUR_BEYOND_HALF);
      }
      // Rule 3: maximal runs strictly on this side.
      int runStart = -1;
      for (int i = 0; i <= n; i++) {
        boolean onSide = i < n && side * (x[i] - mean) > 0;
        if (onSide && runStart < 0) runStart = i;
        if (!onSide && runStart >= 0) {
          if (i - runStart >= RUN_LENGTH)
            for (int j = runStart; j < i; j++) signals.get(j).add(Signal.RUN_OF_EIGHT);
          runStart = -1;
        }
      }
    }
    List<Point> points = new ArrayList<>(n);
    for (int i = 0; i < n; i++)
      points.add(new Point(x[i], i == 0 ? null : ranges[i], signals.get(i)));
    return Optional.of(new XmrChart(mean, mrBar, upper, lower, rangeLimit, points));
  }

  /** Number of points that raise at least one signal. */
  public long signalCount() {
    return points.stream().filter(point -> !point.signals().isEmpty()).count();
  }
}
