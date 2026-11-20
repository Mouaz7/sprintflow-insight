package com.sprintflowinsight.reporting;

import com.sprintflowinsight.forecast.BacktestConfig;
import com.sprintflowinsight.forecast.Backtester;
import com.sprintflowinsight.forecast.ForecastModel;
import com.sprintflowinsight.forecast.ModelSelection;
import com.sprintflowinsight.forecast.ThroughputHistory;
import com.sprintflowinsight.reporting.ForecastBacktestResponse.Interval;
import com.sprintflowinsight.reporting.ForecastBacktestResponse.Prediction;
import com.sprintflowinsight.reporting.ForecastBacktestResponse.Status;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

final class ForecastBacktestCalculator {
  private static final BacktestConfig CONFIG = BacktestConfig.DEFAULT;

  private ForecastBacktestCalculator() {}

  static ForecastBacktestResponse calculate(ForecastBasis basis, ThroughputHistory history) {
    return calculate(basis, history, ForecastCalculator.seed(history));
  }

  static ForecastBacktestResponse calculate(
      ForecastBasis basis, ThroughputHistory history, long seed) {
    String seedText = Long.toUnsignedString(seed);
    if (history.size() < CONFIG.requiredPeriods()) {
      return unavailable(basis, history, seedText, Status.INSUFFICIENT_HISTORY);
    }
    if (Arrays.stream(history.counts()).allMatch(count -> count == 0)) {
      return unavailable(basis, history, seedText, Status.NO_COMPLETIONS);
    }

    var candidates = ForecastModel.defaults(new SplittableRandom(seed));
    var selection = new Backtester(CONFIG).select(history, candidates);
    if (!(selection instanceof ModelSelection.Selected selected)) {
      throw new IllegalStateException("A qualifying history must select a model.");
    }
    // Use a fresh generator for the published prediction, so its result is independent of
    // how many bootstrap resamples were consumed by comparison.
    var model =
        ForecastModel.defaults(new SplittableRandom(seed)).stream()
            .filter(candidate -> candidate.name().equals(selected.best().model()))
            .findFirst()
            .orElseThrow();
    var forecast = model.forecast(history, CONFIG.horizonPeriods());
    var intervals =
        Backtester.INTERVAL_LEVELS.stream()
            .map(
                level -> {
                  double tail = (1 - level) / 2;
                  return new Interval(level, forecast.quantile(tail), forecast.quantile(1 - tail));
                })
            .toList();

    return new ForecastBacktestResponse(
        basis,
        history.unit(),
        history.periodDays(),
        history.size(),
        CONFIG.requiredPeriods(),
        CONFIG.minTrainingPeriods(),
        CONFIG.horizonPeriods(),
        seedText,
        Status.SELECTED,
        model.name(),
        new Prediction(forecast.median(), intervals),
        selected.reports());
  }

  private static ForecastBacktestResponse unavailable(
      ForecastBasis basis, ThroughputHistory history, String seed, Status status) {
    return new ForecastBacktestResponse(
        basis,
        history.unit(),
        history.periodDays(),
        history.size(),
        CONFIG.requiredPeriods(),
        CONFIG.minTrainingPeriods(),
        CONFIG.horizonPeriods(),
        seed,
        status,
        null,
        null,
        List.of());
  }
}
