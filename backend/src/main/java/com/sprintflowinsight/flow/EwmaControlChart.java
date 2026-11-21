package com.sprintflowinsight.flow;

import java.util.ArrayList;
import java.util.List;

/** Exponentially weighted control limits for sequential, complete periods of event counts. */
public final class EwmaControlChart {
  public static final int BASELINE_PERIODS = 28;
  public static final int MONITOR_PERIODS = 7;
  public static final double WEIGHT = 0.25;
  public static final double LIMIT_SIGMAS = 3.0;

  public enum Signal {
    LOW,
    IN_LIMITS,
    HIGH
  }

  public record Point(int observed, double weighted, double lower, double upper, Signal signal) {}

  public record Chart(double baselineMean, List<Point> monitoring) {
    public Chart {
      monitoring = List.copyOf(monitoring);
    }
  }

  private EwmaControlChart() {}

  /** The baseline is fixed before the monitoring window and never uses observations from it. */
  public static Chart compare(List<Integer> baseline, List<Integer> monitoring) {
    if (baseline.size() != BASELINE_PERIODS || monitoring.size() != MONITOR_PERIODS)
      throw new IllegalArgumentException(
          "The chart needs 28 baseline and seven monitoring periods.");
    double mean = baseline.stream().mapToDouble(EwmaControlChart::count).average().orElseThrow();
    double sampleVariance =
        baseline.stream()
                .mapToDouble(
                    value -> {
                      double deviation = value - mean;
                      return deviation * deviation;
                    })
                .sum()
            / (baseline.size() - 1);
    // Counts can be sparse or constant. A Poisson variance floor keeps the limits nonzero.
    double variance = Math.max(0.25, Math.max(mean, sampleVariance));
    double weighted = mean;
    double weightedVariance = 0;
    double retained = 1 - WEIGHT;
    var points = new ArrayList<Point>(monitoring.size());
    for (int count : monitoring) {
      weighted = WEIGHT * count(count) + retained * weighted;
      weightedVariance = WEIGHT * WEIGHT * variance + retained * retained * weightedVariance;
      double spread = LIMIT_SIGMAS * Math.sqrt(weightedVariance);
      double lower = Math.max(0, mean - spread);
      double upper = mean + spread;
      Signal signal =
          weighted < lower ? Signal.LOW : weighted > upper ? Signal.HIGH : Signal.IN_LIMITS;
      points.add(new Point(count, weighted, lower, upper, signal));
    }
    return new Chart(mean, points);
  }

  private static int count(int value) {
    if (value < 0) throw new IllegalArgumentException("Period counts must be non-negative.");
    return value;
  }
}
