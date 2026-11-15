package com.sprintflowinsight.forecast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rolling-origin evaluation of forecast models on a project's own history, and selection of the
 * model with the lowest mean CRPS. Ties keep the earlier candidate.
 */
public final class Backtester {
  /** Nominal central-interval levels whose coverage is reported. */
  public static final List<Double> INTERVAL_LEVELS = List.of(0.50, 0.85, 0.95);

  private final BacktestConfig config;

  public Backtester() {
    this(BacktestConfig.DEFAULT);
  }

  public Backtester(BacktestConfig config) {
    if (config == null) throw new IllegalArgumentException("config is required");
    this.config = config;
  }

  public BacktestConfig config() {
    return config;
  }

  /**
   * Evaluates one model at every origin.
   *
   * @throws IllegalArgumentException if the history yields no origin at all
   */
  public BacktestReport evaluate(ForecastModel model, ThroughputHistory history) {
    int origins = config.origins(history.size());
    if (origins == 0) throw new IllegalArgumentException("history has no forecast origin");
    int horizon = config.horizonPeriods();
    int bins = config.pitBins();
    double crps = 0;
    double absoluteError = 0;
    int[] covered = new int[INTERVAL_LEVELS.size()];
    double[] pitCdf = new double[bins + 1];
    for (int origin = config.minTrainingPeriods(); origin + horizon <= history.size(); origin++) {
      DiscreteDistribution forecast = model.forecast(history.prefix(origin), horizon);
      int observed = history.total(origin, origin + horizon);
      crps += ForecastScores.crps(forecast, observed);
      absoluteError += Math.abs(forecast.median() - observed);
      for (int level = 0; level < covered.length; level++) {
        if (ForecastScores.covers(forecast, observed, INTERVAL_LEVELS.get(level))) covered[level]++;
      }
      for (int bin = 1; bin <= bins; bin++) {
        pitCdf[bin] += ForecastScores.conditionalPit(forecast, observed, (double) bin / bins);
      }
    }
    Map<Double, Double> coverage = new LinkedHashMap<>();
    for (int level = 0; level < covered.length; level++) {
      coverage.put(INTERVAL_LEVELS.get(level), (double) covered[level] / origins);
    }
    List<Double> histogram = new ArrayList<>(bins);
    for (int bin = 1; bin <= bins; bin++) histogram.add((pitCdf[bin] - pitCdf[bin - 1]) / origins);
    return new BacktestReport(
        model.name(), origins, crps / origins, coverage, histogram, absoluteError / origins);
  }

  /** Backtests each candidate and picks the lowest mean CRPS, subject to the minimum-data rule. */
  public ModelSelection select(ThroughputHistory history, List<ForecastModel> candidates) {
    if (candidates.isEmpty()) throw new IllegalArgumentException("no candidate models");
    if (config.origins(history.size()) < config.minOrigins()) {
      return new ModelSelection.InsufficientHistory(history.size(), config.requiredPeriods());
    }
    List<BacktestReport> reports = new ArrayList<>();
    int best = 0;
    for (int i = 0; i < candidates.size(); i++) {
      reports.add(evaluate(candidates.get(i), history));
      if (reports.get(i).meanCrps() < reports.get(best).meanCrps()) best = i;
    }
    return new ModelSelection.Selected(candidates.get(best), reports.get(best), reports);
  }
}
