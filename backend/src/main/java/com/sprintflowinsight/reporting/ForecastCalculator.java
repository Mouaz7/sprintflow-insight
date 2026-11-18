package com.sprintflowinsight.reporting;

import com.sprintflowinsight.forecast.ForecastAnswers;
import com.sprintflowinsight.forecast.MonteCarloSimulation;
import com.sprintflowinsight.forecast.ThroughputHistory;
import com.sprintflowinsight.reporting.ForecastResponse.TargetSource;
import com.sprintflowinsight.reporting.ForecastResponse.Warning;
import com.sprintflowinsight.reporting.ForecastResponse.WorkScope;
import com.sprintflowinsight.reporting.ThroughputDerivation.Derived;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/** Pure B6 answer mapping with reproducible paths and an injectable generator. */
final class ForecastCalculator {
  /** A product guidance threshold, not proof that six periods form a validated baseline. */
  static final int MINIMUM_PERIODS = 6;

  private ForecastCalculator() {}

  record Inputs(
      ForecastBasis basis,
      Derived derived,
      LocalDate today,
      int remaining,
      Target target,
      WorkScope scope) {
    Inputs {
      if (basis == null || derived == null || today == null || target == null || scope == null)
        throw new IllegalArgumentException("forecast inputs are required");
      if (remaining < 0) throw new IllegalArgumentException("remaining must not be negative");
    }
  }

  record Target(LocalDate date, int items, TargetSource source) {
    Target {
      if (date == null || source == null || items < 0)
        throw new IllegalArgumentException("invalid target");
    }
  }

  static ForecastResponse calculate(Inputs inputs) {
    return calculate(inputs, new SplittableRandom(seed(inputs.derived().history())));
  }

  static ForecastResponse calculate(Inputs inputs, RandomGenerator random) {
    var history = inputs.derived().history();
    long requestedDays = ChronoUnit.DAYS.between(inputs.today(), inputs.target().date());
    int days = (int) Math.max(0, Math.min(MonteCarloSimulation.MAX_DAYS, requestedDays));
    var simulated =
        Arrays.stream(history.counts()).anyMatch(count -> count > 0)
            ? MonteCarloSimulation.simulate(
                history, inputs.remaining(), inputs.target().items(), days, random)
            : null;
    var warnings = new ArrayList<Warning>();
    if (history.size() == 0) warnings.add(Warning.NO_HISTORY);
    else if (Arrays.stream(history.counts()).noneMatch(count -> count > 0))
      warnings.add(Warning.NO_COMPLETIONS);
    if (history.size() > 0 && history.size() < MINIMUM_PERIODS)
      warnings.add(Warning.INSUFFICIENT_HISTORY);
    var scope = inputs.scope();
    if (scope.tasksWithoutHistory() > 0) warnings.add(Warning.MISSING_TASK_HISTORY);
    if (scope.unplannedStories() > 0) warnings.add(Warning.UNPLANNED_STORIES);
    if (inputs.basis() == ForecastBasis.VELOCITY && scope.zeroPointTasks() > 0)
      warnings.add(Warning.ZERO_POINT_TASKS);
    if (scope.epicId() != null) warnings.add(Warning.EPIC_USES_PROJECT_HISTORY);
    if (requestedDays < 0) warnings.add(Warning.PAST_SPRINT_DEADLINE);
    if (requestedDays > MonteCarloSimulation.MAX_DAYS) warnings.add(Warning.TARGET_BEYOND_HORIZON);
    Double probability = null;
    if (requestedDays <= MonteCarloSimulation.MAX_DAYS) {
      if (simulated != null) probability = simulated.targetProbability();
      else if (inputs.target().items() == 0) probability = 1.0;
    }
    return new ForecastResponse(
        inputs.basis(),
        history.unit(),
        history.periodDays(),
        inputs.today(),
        Long.toUnsignedString(seed(history)),
        simulated == null ? 0 : simulated.trials(),
        MINIMUM_PERIODS,
        inputs.derived().periods().stream()
            .map(p -> new ForecastResponse.HistoryPeriod(p.start(), p.end(), p.count(), p.label()))
            .toList(),
        warnings,
        scope,
        inputs.remaining(),
        simulated == null
            ? ForecastAnswers.CONFIDENCE_LEVELS.stream()
                .map(
                    level ->
                        new ForecastResponse.CompletionDate(
                            level, inputs.remaining() == 0 ? inputs.today() : null))
                .toList()
            : simulated.completions().stream()
                .map(
                    row ->
                        new ForecastResponse.CompletionDate(
                            row.confidence(),
                            row.days() == null ? null : inputs.today().plusDays(row.days())))
                .toList(),
        simulated == null ? List.of() : simulated.completionHistogram(),
        simulated == null ? 0 : simulated.beyondHorizon(),
        MonteCarloSimulation.MAX_DAYS,
        simulated == null || requestedDays > MonteCarloSimulation.MAX_DAYS
            ? null
            : new ForecastResponse.ItemsByDate(
                inputs.target().date(),
                inputs.target().source(),
                simulated.quantities().stream()
                    .map(
                        row ->
                            new ForecastResponse.ItemsAtConfidence(row.confidence(), row.items()))
                    .toList(),
                simulated.quantityHistogram()),
        new ForecastResponse.TargetProbability(
            inputs.target().items(),
            inputs.target().date(),
            inputs.target().source(),
            probability));
  }

  /** FNV-1a over immutable history inputs; no user identifiers or current random state. */
  static long seed(ThroughputHistory history) {
    long hash = 0xcbf29ce484222325L;
    hash = (hash ^ history.unit().ordinal()) * 0x100000001b3L;
    hash = (hash ^ history.periodDays()) * 0x100000001b3L;
    for (int count : history.counts()) hash = (hash ^ count) * 0x100000001b3L;
    return hash;
  }
}
