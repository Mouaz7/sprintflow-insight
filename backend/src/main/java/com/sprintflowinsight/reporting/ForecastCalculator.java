package com.sprintflowinsight.reporting;

import com.sprintflowinsight.forecast.BacktestConfig;
import com.sprintflowinsight.forecast.BacktestReport;
import com.sprintflowinsight.forecast.Backtester;
import com.sprintflowinsight.forecast.BootstrapMonteCarloModel;
import com.sprintflowinsight.forecast.DiscreteDistribution;
import com.sprintflowinsight.forecast.EwmaPoissonModel;
import com.sprintflowinsight.forecast.ForecastAnswers;
import com.sprintflowinsight.forecast.ForecastModel;
import com.sprintflowinsight.forecast.GammaPoissonModel;
import com.sprintflowinsight.forecast.ModelSelection;
import com.sprintflowinsight.forecast.ThroughputHistory;
import com.sprintflowinsight.reporting.ForecastResponse.CompletionDate;
import com.sprintflowinsight.reporting.ForecastResponse.FanPoint;
import com.sprintflowinsight.reporting.ForecastResponse.HistoryPeriod;
import com.sprintflowinsight.reporting.ForecastResponse.ItemsAtConfidence;
import com.sprintflowinsight.reporting.ForecastResponse.ItemsByDate;
import com.sprintflowinsight.reporting.ForecastResponse.ModelChoice;
import com.sprintflowinsight.reporting.ForecastResponse.ModelScore;
import com.sprintflowinsight.reporting.ForecastResponse.SelectionStatus;
import com.sprintflowinsight.reporting.ForecastResponse.TargetProbability;
import com.sprintflowinsight.reporting.ForecastResponse.TargetSource;
import com.sprintflowinsight.reporting.ThroughputDerivation.Derived;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Answers the planning questions for one derived history. Pure and deterministic: Monte Carlo
 * draws come from generators seeded by the history, so equal inputs always give equal output, and
 * the output may be cached by its inputs.
 */
final class ForecastCalculator {
  /** Number of future periods in the fan chart. */
  static final int FAN_PERIODS = 12;

  private static final double[] FAN_LEVELS = {0.05, 0.25, 0.50, 0.75, 0.95};

  /** Separates the generator for answers from the one used while backtesting. */
  private static final long ANSWER_STREAM = 0x5DEECE66DL;

  private ForecastCalculator() {}

  /** Everything a forecast depends on. Records compare by value, so this is also a cache key. */
  record Inputs(
      ForecastBasis basis, Derived derived, LocalDate today, int remaining, Target target) {
    Inputs {
      if (basis == null || derived == null || today == null) {
        throw new IllegalArgumentException("basis, history and today are required");
      }
      if (remaining < 0) throw new IllegalArgumentException("remaining must not be negative");
    }
  }

  /** A target: {@code items} of work by {@code date}. */
  record Target(LocalDate date, int items, TargetSource source) {
    Target {
      if (date == null || source == null) throw new IllegalArgumentException("date is required");
      if (items < 0) throw new IllegalArgumentException("items must not be negative");
    }
  }

  static ForecastResponse calculate(Inputs inputs) {
    ThroughputHistory history = inputs.derived().history();
    long seed = seed(history);
    List<HistoryPeriod> periods =
        inputs.derived().periods().stream()
            .map(p -> new HistoryPeriod(p.start(), p.end(), p.count(), p.label()))
            .toList();
    int required = BacktestConfig.DEFAULT.requiredPeriods();
    if (history.total(0, history.size()) == 0) {
      return new ForecastResponse(
          inputs.basis(),
          history.unit(),
          history.periodDays(),
          inputs.today(),
          seed,
          periods,
          new ModelChoice(SelectionStatus.NO_HISTORY, null, history.size(), required, List.of()),
          inputs.remaining(),
          List.of(),
          null,
          null,
          List.of());
    }
    ModelChoice choice = choose(history, seed);
    ForecastModel model = create(choice.name(), new SplittableRandom(seed ^ ANSWER_STREAM));
    ForecastAnswers answers = new ForecastAnswers(model, history);

    List<CompletionDate> completion = new ArrayList<>();
    for (Map.Entry<Double, Optional<LocalDate>> entry :
        answers.completionDates(inputs.remaining(), inputs.today()).entrySet()) {
      completion.add(new CompletionDate(entry.getKey(), entry.getValue().orElse(null)));
    }

    ItemsByDate byDate = null;
    TargetProbability target = null;
    Target wanted = inputs.target();
    if (wanted != null) {
      List<ItemsAtConfidence> items = new ArrayList<>();
      answers
          .itemsByDate(inputs.today(), wanted.date())
          .forEach((level, count) -> items.add(new ItemsAtConfidence(level, count)));
      byDate = new ItemsByDate(wanted.date(), wanted.source(), items);
      long days = ChronoUnit.DAYS.between(inputs.today(), wanted.date());
      target =
          new TargetProbability(
              wanted.items(),
              wanted.date(),
              wanted.source(),
              answers.probabilityToReach(wanted.items(), days));
    }

    return new ForecastResponse(
        inputs.basis(),
        history.unit(),
        history.periodDays(),
        inputs.today(),
        seed,
        periods,
        choice,
        inputs.remaining(),
        completion,
        byDate,
        target,
        fan(model, history, inputs.today()));
  }

  /**
   * Backtests the three candidate models and keeps the lowest mean CRPS. With too little history
   * the Gamma-Poisson model is used: its prior widens the intervals when data are scarce instead
   * of trusting a handful of weeks.
   */
  static ModelChoice choose(ThroughputHistory history, long seed) {
    ModelSelection selection =
        new Backtester().select(history, ForecastModel.defaults(new SplittableRandom(seed)));
    int required = BacktestConfig.DEFAULT.requiredPeriods();
    return switch (selection) {
      case ModelSelection.Selected selected -> {
        String best = selected.model().name();
        List<ModelScore> scores =
            selected.reports().stream()
                .sorted(Comparator.comparingDouble(BacktestReport::meanCrps))
                .map(report -> score(report, report.model().equals(best)))
                .toList();
        yield new ModelChoice(
            SelectionStatus.SELECTED, best, history.size(), required, scores);
      }
      case ModelSelection.InsufficientHistory insufficient ->
          new ModelChoice(
              SelectionStatus.INSUFFICIENT_HISTORY,
              GammaPoissonModel.NAME,
              insufficient.availablePeriods(),
              insufficient.requiredPeriods(),
              List.of());
    };
  }

  /** Predictive quantiles of cumulative work after 1 … {@link #FAN_PERIODS} periods. */
  static List<FanPoint> fan(ForecastModel model, ThroughputHistory history, LocalDate start) {
    List<FanPoint> points = new ArrayList<>(FAN_PERIODS);
    for (int period = 1; period <= FAN_PERIODS; period++) {
      DiscreteDistribution distribution = model.forecast(history, period);
      int[] q = new int[FAN_LEVELS.length];
      for (int i = 0; i < q.length; i++) q[i] = distribution.quantile(FAN_LEVELS[i]);
      points.add(
          new FanPoint(
              start.plusDays((long) period * history.periodDays()), q[0], q[1], q[2], q[3], q[4]));
    }
    return points;
  }

  /** A fresh instance of the named model with default settings. */
  static ForecastModel create(String name, RandomGenerator random) {
    return switch (name) {
      case GammaPoissonModel.NAME -> new GammaPoissonModel();
      case EwmaPoissonModel.NAME -> new EwmaPoissonModel();
      case BootstrapMonteCarloModel.NAME -> new BootstrapMonteCarloModel(random);
      default -> throw new IllegalArgumentException("unknown model " + name);
    };
  }

  /**
   * Seed derived from the history alone (FNV-1a over unit, period length and counts), so the
   * forecast changes only when the data change and never between identical requests.
   */
  static long seed(ThroughputHistory history) {
    long hash = 0xcbf29ce484222325L;
    hash = mix(hash, history.unit().ordinal());
    hash = mix(hash, history.periodDays());
    for (int count : history.counts()) hash = mix(hash, count);
    return hash;
  }

  private static long mix(long hash, int value) {
    return (hash ^ value) * 0x100000001b3L;
  }

  private static ModelScore score(BacktestReport report, boolean selected) {
    return new ModelScore(
        report.model(),
        selected,
        report.origins(),
        report.meanCrps(),
        report.medianAbsoluteError(),
        report.coverage().get(0.50),
        report.coverage().get(0.85),
        report.coverage().get(0.95));
  }
}
