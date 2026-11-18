package com.sprintflowinsight.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprintflowinsight.forecast.ThroughputHistory;
import com.sprintflowinsight.forecast.ThroughputUnit;
import com.sprintflowinsight.reporting.ForecastResponse.TargetSource;
import com.sprintflowinsight.reporting.ForecastResponse.Warning;
import com.sprintflowinsight.reporting.ForecastResponse.WorkScope;
import com.sprintflowinsight.reporting.ThroughputDerivation.Derived;
import java.time.LocalDate;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class ForecastCalculatorTest {
  static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
  static final WorkScope SCOPE = new WorkScope(null, null, 10, 20, 0, 0, 0);

  @Test
  void mapsKnownConstantCapacityToDatesAmountsAndTargetProbability() {
    var inputs = inputs(ThroughputHistory.weekly(7, 7, 7, 7, 7, 7), 10, 14, 12, SCOPE);
    var result = ForecastCalculator.calculate(inputs, new SplittableRandom(60));
    assertThat(result.simulations()).isEqualTo(10_000);
    assertThat(result.minimumPeriods()).isEqualTo(6);
    assertThat(result.warnings()).isEmpty();
    assertThat(result.completionDates()).allMatch(row -> row.date().equals(TODAY.plusDays(10)));
    assertThat(result.itemsByDate().items()).allMatch(row -> row.items() == 14);
    assertThat(result.target().probability()).isEqualTo(1);
    assertThat(result.seed())
        .isEqualTo(Long.toUnsignedString(ForecastCalculator.seed(inputs.derived().history())));
  }

  @Test
  void reportsUnavailableProgressAndSparseHistoryWithoutPretendingToBacktest() {
    var empty = ForecastCalculator.calculate(inputs(ThroughputHistory.weekly(), 5, 7, 3, SCOPE));
    assertThat(empty.warnings()).containsExactly(Warning.NO_HISTORY);
    assertThat(empty.completionDates()).allMatch(row -> row.date() == null);
    assertThat(empty.beyondHorizon()).isZero();
    assertThat(empty.simulations()).isZero();
    assertThat(empty.itemsByDate()).isNull();
    assertThat(empty.target().probability()).isNull();
    var trivial = ForecastCalculator.calculate(inputs(ThroughputHistory.weekly(), 0, 0, 0, SCOPE));
    assertThat(trivial.target().probability()).isEqualTo(1);
    assertThat(trivial.completionDates()).allMatch(row -> row.date().equals(TODAY));
    var zero =
        ForecastCalculator.calculate(inputs(ThroughputHistory.weekly(0, 0, 0), 5, 7, 3, SCOPE));
    assertThat(zero.warnings())
        .containsExactly(Warning.NO_COMPLETIONS, Warning.INSUFFICIENT_HISTORY);
    var sparse = ForecastCalculator.calculate(inputs(ThroughputHistory.weekly(3), 5, 7, 3, SCOPE));
    assertThat(sparse.warnings()).containsExactly(Warning.INSUFFICIENT_HISTORY);
  }

  @Test
  void warnsAboutIncompleteWorkAndConditionalEpicAllocation() {
    var scope = new WorkScope(9L, "Release", 4, 5, 1, 2, 3);
    var inputs =
        new ForecastCalculator.Inputs(
            ForecastBasis.VELOCITY,
            new Derived(ThroughputHistory.sprintVelocity(14, 5, 8), List.of()),
            TODAY,
            5,
            new ForecastCalculator.Target(TODAY.plusDays(28), 5, TargetSource.ACTIVE_SPRINT),
            scope);
    var result = ForecastCalculator.calculate(inputs);
    assertThat(result.warnings())
        .contains(
            Warning.INSUFFICIENT_HISTORY,
            Warning.MISSING_TASK_HISTORY,
            Warning.UNPLANNED_STORIES,
            Warning.ZERO_POINT_TASKS,
            Warning.EPIC_USES_PROJECT_HISTORY);
    assertThat(result.target().source()).isEqualTo(TargetSource.ACTIVE_SPRINT);
    assertThat(result.scope()).isEqualTo(scope);
  }

  @Test
  void handlesOverdueAndUnforecastablyDistantSprintDeadlines() {
    var past = ForecastCalculator.calculate(inputs(ThroughputHistory.weekly(7), 1, -1, 1, SCOPE));
    assertThat(past.warnings()).contains(Warning.PAST_SPRINT_DEADLINE);
    assertThat(past.target().probability()).isZero();
    assertThat(past.itemsByDate().items()).allMatch(row -> row.items() == 0);
    var distant =
        ForecastCalculator.calculate(inputs(ThroughputHistory.weekly(7), 1, 2000, 1, SCOPE));
    assertThat(distant.warnings()).contains(Warning.TARGET_BEYOND_HORIZON);
    assertThat(distant.target().probability()).isNull();
    assertThat(distant.itemsByDate()).isNull();
  }

  @Test
  void seedsReflectTheOrderedHistoryAndUnitAndEqualRequestsAreReproducible() {
    var history = ThroughputHistory.weekly(1, 2, 0, 5);
    var inputs = inputs(history, 8, 21, 4, SCOPE);
    assertThat(ForecastCalculator.calculate(inputs))
        .isEqualTo(ForecastCalculator.calculate(inputs));
    assertThat(ForecastCalculator.seed(history))
        .isNotEqualTo(ForecastCalculator.seed(ThroughputHistory.weekly(2, 1, 0, 5)))
        .isNotEqualTo(
            ForecastCalculator.seed(ThroughputHistory.of(14, ThroughputUnit.ITEMS, 1, 2, 0, 5)))
        .isNotEqualTo(ForecastCalculator.seed(ThroughputHistory.sprintVelocity(7, 1, 2, 0, 5)));
    assertThatThrownBy(() -> new ForecastCalculator.Inputs(null, null, null, -1, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  static ForecastCalculator.Inputs inputs(
      ThroughputHistory history, int remaining, int targetDays, int target, WorkScope scope) {
    return new ForecastCalculator.Inputs(
        ForecastBasis.THROUGHPUT,
        new Derived(history, List.of()),
        TODAY,
        remaining,
        new ForecastCalculator.Target(TODAY.plusDays(targetDays), target, TargetSource.REQUEST),
        scope);
  }
}
