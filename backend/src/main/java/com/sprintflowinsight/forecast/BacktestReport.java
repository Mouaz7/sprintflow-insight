package com.sprintflowinsight.forecast;

import java.util.List;
import java.util.Map;

/**
 * Out-of-sample accuracy of one model over all rolling origins.
 *
 * @param meanCrps mean CRPS, in the history's unit (lower is better)
 * @param coverage share of observations inside the central 50/85/95 % intervals, by level
 * @param pitHistogram non-randomised PIT histogram; each bin is the share of cases (sums to one)
 * @param medianAbsoluteError mean absolute error of the forecast median (MAE)
 */
public record BacktestReport(
    String model,
    int origins,
    double meanCrps,
    Map<Double, Double> coverage,
    List<Double> pitHistogram,
    double medianAbsoluteError) {
  public BacktestReport {
    coverage = Map.copyOf(coverage);
    pitHistogram = List.copyOf(pitHistogram);
  }
}
