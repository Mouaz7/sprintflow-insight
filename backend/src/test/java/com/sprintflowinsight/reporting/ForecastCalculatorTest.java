package com.sprintflowinsight.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprintflowinsight.forecast.BootstrapMonteCarloModel;
import com.sprintflowinsight.forecast.EwmaPoissonModel;
import com.sprintflowinsight.forecast.GammaPoissonModel;
import com.sprintflowinsight.forecast.ThroughputHistory;
import com.sprintflowinsight.forecast.ThroughputUnit;
import com.sprintflowinsight.reporting.ForecastCalculator.Inputs;
import com.sprintflowinsight.reporting.ForecastCalculator.Target;
import com.sprintflowinsight.reporting.ForecastResponse.CompletionDate;
import com.sprintflowinsight.reporting.ForecastResponse.FanPoint;
import com.sprintflowinsight.reporting.ForecastResponse.ItemsAtConfidence;
import com.sprintflowinsight.reporting.ForecastResponse.ModelScore;
import com.sprintflowinsight.reporting.ForecastResponse.SelectionStatus;
import com.sprintflowinsight.reporting.ForecastResponse.TargetSource;
import com.sprintflowinsight.reporting.ThroughputDerivation.Derived;
import com.sprintflowinsight.reporting.ThroughputDerivation.Period;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class ForecastCalculatorTest {
  private static final LocalDate TODAY = LocalDate.parse("2026-11-18");

  @Test
  void withoutAnyCompletedWorkThereIsNoForecast() {
    ForecastResponse response =
        ForecastCalculator.calculate(inputs(weekly(0, 0, 0), 5, target(10, 3)));

    assertThat(response.model().status()).isEqualTo(SelectionStatus.NO_HISTORY);
    assertThat(response.model().name()).isNull();
    assertThat(response.model().availablePeriods()).isEqualTo(3);
    assertThat(response.model().requiredPeriods()).isEqualTo(15);
    assertThat(response.remaining()).isEqualTo(5);
    assertThat(response.completionDates()).isEmpty();
    assertThat(response.itemsByDate()).isNull();
    assertThat(response.target()).isNull();
    assertThat(response.fan()).isEmpty();
    assertThat(response.history()).hasSize(3);
  }

  @Test
  void shortHistoryFallsBackToGammaPoissonWithoutBacktest() {
    ForecastResponse response = ForecastCalculator.calculate(inputs(weekly(3, 4, 2, 5), 10, null));

    assertThat(response.model().status()).isEqualTo(SelectionStatus.INSUFFICIENT_HISTORY);
    assertThat(response.model().name()).isEqualTo(GammaPoissonModel.NAME);
    assertThat(response.model().availablePeriods()).isEqualTo(4);
    assertThat(response.model().backtest()).isEmpty();
    assertThat(response.completionDates()).hasSize(3);
    assertThat(response.fan()).hasSize(ForecastCalculator.FAN_PERIODS);
  }

  @Test
  void longHistorySelectsTheLowestCrpsAndListsEveryCandidateBestFirst() {
    ForecastResponse response = ForecastCalculator.calculate(inputs(steady(30), 20, null));

    assertThat(response.model().status()).isEqualTo(SelectionStatus.SELECTED);
    List<ModelScore> scores = response.model().backtest();
    assertThat(scores)
        .extracting(ModelScore::model)
        .containsExactlyInAnyOrder(
            GammaPoissonModel.NAME, EwmaPoissonModel.NAME, BootstrapMonteCarloModel.NAME);
    assertThat(scores.get(0).selected()).isTrue();
    assertThat(scores.get(0).model()).isEqualTo(response.model().name());
    assertThat(scores.stream().filter(ModelScore::selected)).hasSize(1);
    for (int i = 1; i < scores.size(); i++) {
      assertThat(scores.get(i).meanCrps()).isGreaterThanOrEqualTo(scores.get(i - 1).meanCrps());
    }
    for (ModelScore score : scores) {
      assertThat(score.origins()).isEqualTo(30 - 8 - 2 + 1);
      assertThat(score.coverage50()).isBetween(0.0, 1.0);
      assertThat(score.coverage85()).isGreaterThanOrEqualTo(score.coverage50());
      assertThat(score.coverage95()).isGreaterThanOrEqualTo(score.coverage85());
      assertThat(score.medianAbsoluteError()).isNotNegative();
    }
  }

  @Test
  void completionDatesAreLaterForHigherConfidence() {
    ForecastResponse response = ForecastCalculator.calculate(inputs(steady(20), 40, null));

    List<CompletionDate> dates = response.completionDates();
    assertThat(dates).extracting(CompletionDate::confidence).containsExactly(0.50, 0.85, 0.95);
    assertThat(dates).allSatisfy(date -> assertThat(date.date()).isAfter(TODAY));
    assertThat(dates.get(1).date()).isAfterOrEqualTo(dates.get(0).date());
    assertThat(dates.get(2).date()).isAfterOrEqualTo(dates.get(1).date());
    // About five items a week, so forty items take roughly eight weeks.
    assertThat(dates.get(0).date()).isBetween(TODAY.plusWeeks(6), TODAY.plusWeeks(10));
  }

  @Test
  void nothingRemainingIsDoneToday() {
    ForecastResponse response = ForecastCalculator.calculate(inputs(steady(20), 0, null));
    assertThat(response.completionDates())
        .allSatisfy(date -> assertThat(date.date()).isEqualTo(TODAY));
  }

  @Test
  void workBeyondTheHorizonHasNoDate() {
    ForecastResponse response =
        ForecastCalculator.calculate(inputs(weekly(1, 0, 0, 0, 0, 0, 0, 0), 10_000, null));
    assertThat(response.completionDates()).allSatisfy(date -> assertThat(date.date()).isNull());
  }

  @Test
  void answersHowMuchByADateAndTheProbabilityOfTheTarget() {
    ForecastResponse easy = ForecastCalculator.calculate(inputs(steady(20), 40, target(28, 5)));
    ForecastResponse hard = ForecastCalculator.calculate(inputs(steady(20), 40, target(28, 60)));

    assertThat(easy.itemsByDate().date()).isEqualTo(TODAY.plusDays(28));
    assertThat(easy.itemsByDate().source()).isEqualTo(TargetSource.REQUEST);
    List<ItemsAtConfidence> items = easy.itemsByDate().items();
    assertThat(items).extracting(ItemsAtConfidence::confidence).containsExactly(0.50, 0.85, 0.95);
    assertThat(items.get(0).items()).isGreaterThanOrEqualTo(items.get(1).items());
    assertThat(items.get(1).items()).isGreaterThanOrEqualTo(items.get(2).items());
    assertThat(items.get(0).items()).isBetween(15, 25);

    assertThat(easy.target().items()).isEqualTo(5);
    assertThat(easy.target().date()).isEqualTo(TODAY.plusDays(28));
    assertThat(easy.target().probability()).isGreaterThan(0.95);
    assertThat(hard.target().probability()).isLessThan(0.05);
  }

  @Test
  void aZeroTargetIsCertainAndATargetDueTodayIsOutOfReach() {
    ForecastResponse zero = ForecastCalculator.calculate(inputs(steady(20), 4, target(7, 0)));
    ForecastResponse today = ForecastCalculator.calculate(inputs(steady(20), 4, target(0, 1)));
    assertThat(zero.target().probability()).isEqualTo(1.0);
    assertThat(today.target().probability()).isZero();
  }

  @Test
  void fanQuantilesAreOrderedAndGrowOverTime() {
    ForecastResponse response = ForecastCalculator.calculate(inputs(steady(20), 10, null));

    List<FanPoint> fan = response.fan();
    assertThat(fan.get(0).date()).isEqualTo(TODAY.plusDays(7));
    assertThat(fan.get(fan.size() - 1).date()).isEqualTo(TODAY.plusDays(7L * fan.size()));
    for (int i = 0; i < fan.size(); i++) {
      FanPoint p = fan.get(i);
      assertThat(p.p05()).isLessThanOrEqualTo(p.p25());
      assertThat(p.p25()).isLessThanOrEqualTo(p.p50());
      assertThat(p.p50()).isLessThanOrEqualTo(p.p75());
      assertThat(p.p75()).isLessThanOrEqualTo(p.p95());
      if (i > 0) assertThat(p.p50()).isGreaterThanOrEqualTo(fan.get(i - 1).p50());
    }
  }

  @Test
  void equalInputsGiveEqualResponsesAndTheSeedFollowsTheData() {
    Inputs inputs = inputs(steady(20), 25, target(14, 8));
    assertThat(ForecastCalculator.calculate(inputs)).isEqualTo(ForecastCalculator.calculate(inputs));

    long seed = ForecastCalculator.seed(steady(20).history());
    assertThat(seed).isEqualTo(ForecastCalculator.seed(steady(20).history()));
    assertThat(seed).isNotEqualTo(ForecastCalculator.seed(weekly(5, 5, 5).history()));
    assertThat(ForecastCalculator.seed(ThroughputHistory.weekly(1, 2)))
        .isNotEqualTo(ForecastCalculator.seed(ThroughputHistory.weekly(2, 1)))
        .isNotEqualTo(ForecastCalculator.seed(ThroughputHistory.sprintVelocity(7, 1, 2)))
        .isNotEqualTo(ForecastCalculator.seed(ThroughputHistory.of(14, ThroughputUnit.ITEMS, 1, 2)));
  }

  @Test
  void createsEachCandidateByName() {
    SplittableRandom random = new SplittableRandom(1);
    assertThat(ForecastCalculator.create(GammaPoissonModel.NAME, random))
        .isInstanceOf(GammaPoissonModel.class);
    assertThat(ForecastCalculator.create(EwmaPoissonModel.NAME, random))
        .isInstanceOf(EwmaPoissonModel.class);
    assertThat(ForecastCalculator.create(BootstrapMonteCarloModel.NAME, random))
        .isInstanceOf(BootstrapMonteCarloModel.class);
    assertThatThrownBy(() -> ForecastCalculator.create("crystal-ball", random))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsIncompleteInputs() {
    Derived derived = steady(3);
    assertThatThrownBy(() -> new Inputs(null, derived, TODAY, 1, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Inputs(ForecastBasis.THROUGHPUT, null, TODAY, 1, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Inputs(ForecastBasis.THROUGHPUT, derived, null, 1, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Inputs(ForecastBasis.THROUGHPUT, derived, TODAY, -1, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Target(null, 1, TargetSource.REQUEST))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Target(TODAY, 1, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Target(TODAY, -1, TargetSource.REQUEST))
        .isInstanceOf(IllegalArgumentException.class);
  }

  static Inputs inputs(Derived derived, int remaining, Target target) {
    return new Inputs(ForecastBasis.THROUGHPUT, derived, TODAY, remaining, target);
  }

  static Target target(int days, int items) {
    return new Target(TODAY.plusDays(days), items, TargetSource.REQUEST);
  }

  /** A fairly steady team: four to six items a week. */
  static Derived steady(int weeks) {
    int[] counts = new int[weeks];
    for (int i = 0; i < weeks; i++) counts[i] = 4 + i % 3;
    return weekly(counts);
  }

  static Derived weekly(int... counts) {
    List<Period> periods = new ArrayList<>();
    LocalDate start = TODAY.minusWeeks(counts.length);
    for (int i = 0; i < counts.length; i++) {
      periods.add(
          new Period(start.plusWeeks(i), start.plusWeeks(i).plusDays(6), counts[i], null));
    }
    return new Derived(ThroughputHistory.weekly(counts), periods);
  }
}
