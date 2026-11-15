package com.sprintflowinsight.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ForecastAnswersTest {
  private static final LocalDate START = LocalDate.of(2026, 3, 2);
  private static final ThroughputHistory WEEKLY_SEVEN = ThroughputHistory.weekly(7, 7, 7, 7);

  /** Exactly five items per week, accruing evenly: floor(5 · periods) items. */
  private static final ForecastModel FIVE_PER_WEEK =
      new ForecastModel() {
        public String name() {
          return "five";
        }

        public DiscreteDistribution forecast(ThroughputHistory history, double periods) {
          return DiscreteDistribution.pointMass((int) Math.floor(5 * periods + 1e-9));
        }
      };

  @Test
  void completionDateOfADeterministicPaceIsExact() {
    var answers = new ForecastAnswers(FIVE_PER_WEEK, WEEKLY_SEVEN);
    // 10 items need two weeks; 11 items need 15.4 days, so day 16.
    assertThat(answers.completionDate(10, START, 0.85)).contains(START.plusDays(14));
    assertThat(answers.completionDate(11, START, 0.5)).contains(START.plusDays(16));
    assertThat(answers.completionDate(1, START, 0.95)).contains(START.plusDays(2));
    assertThat(answers.completionDate(0, START, 0.95)).contains(START);
    assertThat(answers.completionDate(-3, START, 0.95)).contains(START);
  }

  @Test
  void completionBeyondTheMaximumIsNotForecast() {
    var answers = new ForecastAnswers(FIVE_PER_WEEK, WEEKLY_SEVEN, 70);
    assertThat(answers.completionDate(50, START, 0.5)).contains(START.plusDays(70));
    assertThat(answers.completionDate(51, START, 0.5)).isEmpty();
    var stalled = new ForecastAnswers(new EwmaPoissonModel(), ThroughputHistory.weekly(0, 0));
    assertThat(stalled.completionDate(1, START, 0.5)).isEmpty();
    assertThat(stalled.completionDates(1, START))
        .containsExactly(
            Map.entry(0.5, Optional.empty()),
            Map.entry(0.85, Optional.empty()),
            Map.entry(0.95, Optional.empty()));
  }

  @Test
  void completionDatesAreTheFirstDaysThatReachEachConfidence() {
    var answers = new ForecastAnswers(new EwmaPoissonModel(), WEEKLY_SEVEN);
    Map<Double, Optional<LocalDate>> dates = answers.completionDates(20, START);
    assertThat(dates).containsOnlyKeys(0.5, 0.85, 0.95);
    for (var entry : dates.entrySet()) {
      long days = START.until(entry.getValue().orElseThrow()).getDays();
      assertThat(answers.probabilityToReach(20, days)).isGreaterThanOrEqualTo(entry.getKey());
      assertThat(answers.probabilityToReach(20, days - 1)).isLessThan(entry.getKey());
    }
    // One item per day: Poisson(d) reaches 20 with 50 % around day 20.
    assertThat(dates.get(0.5)).contains(START.plusDays(20));
    assertThat(dates.get(0.5).get()).isBefore(dates.get(0.85).get());
    assertThat(dates.get(0.85).get()).isBefore(dates.get(0.95).get());
  }

  @Test
  void completionDateSearchCoversDatesJustBelowAPowerOfTwo() {
    var answers = new ForecastAnswers(FIVE_PER_WEEK, WEEKLY_SEVEN);
    for (int items = 1; items <= 60; items++) {
      long expected = (long) Math.ceil(items * 7 / 5.0 - 1e-9);
      assertThat(answers.completionDate(items, START, 0.5))
          .as("items %d", items)
          .contains(START.plusDays(expected));
    }
  }

  @Test
  void probabilityToReachUsesFractionalPeriods() {
    var answers = new ForecastAnswers(new EwmaPoissonModel(), WEEKLY_SEVEN);
    double expected = 1 - DiscreteDistribution.poisson(5).cdf(4);
    assertThat(answers.probabilityToReach(5, 5)).isCloseTo(expected, within(1e-12));
    assertThat(answers.probabilityToReach(0, 0)).isEqualTo(1.0);
    assertThat(answers.probabilityToReach(1, 0)).isZero();
    assertThatThrownBy(() -> answers.probabilityToReach(1, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void itemsByDateReportsWhatIsReachedAtEachConfidence() {
    var exact = new ForecastAnswers(FIVE_PER_WEEK, WEEKLY_SEVEN);
    assertThat(exact.itemsByDate(START, START.plusDays(21)))
        .containsExactly(Map.entry(0.5, 15), Map.entry(0.85, 15), Map.entry(0.95, 15));
    var poisson = new ForecastAnswers(new EwmaPoissonModel(), WEEKLY_SEVEN);
    Map<Double, Integer> items = poisson.itemsByDate(START, START.plusDays(14));
    assertThat(items.get(0.5)).isEqualTo(14);
    assertThat(items.get(0.85)).isLessThan(items.get(0.5));
    assertThat(items.get(0.95)).isLessThan(items.get(0.85));
    for (var entry : items.entrySet()) {
      assertThat(poisson.probabilityToReach(entry.getValue(), 14))
          .isGreaterThanOrEqualTo(entry.getKey());
      assertThat(poisson.probabilityToReach(entry.getValue() + 1, 14)).isLessThan(entry.getKey());
    }
    assertThat(poisson.itemsByDate(START, START)).containsEntry(0.95, 0);
    assertThatThrownBy(() -> poisson.itemsByDate(START, START.minusDays(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void itemsAtConfidenceIsTheLargestCountReachedWithThatProbability() {
    DiscreteDistribution d = DiscreteDistribution.ofWeights(0, new double[] {0.1, 0.2, 0.3, 0.4});
    assertThat(ForecastAnswers.itemsAtConfidence(d, 0.4)).isEqualTo(3);
    assertThat(ForecastAnswers.itemsAtConfidence(d, 0.5)).isEqualTo(2);
    assertThat(ForecastAnswers.itemsAtConfidence(d, 0.7)).isEqualTo(2);
    assertThat(ForecastAnswers.itemsAtConfidence(d, 0.85)).isEqualTo(1);
    assertThat(ForecastAnswers.itemsAtConfidence(d, 0.9)).isEqualTo(1);
    assertThat(ForecastAnswers.itemsAtConfidence(d, 0.95)).isZero();
    assertThat(ForecastAnswers.itemsAtConfidence(d, 1)).isZero();
    assertThat(ForecastAnswers.itemsAtConfidence(DiscreteDistribution.pointMass(6), 1)).isEqualTo(6);
  }

  @Test
  void invalidArgumentsAreRejected() {
    assertThatThrownBy(() -> new ForecastAnswers(null, WEEKLY_SEVEN))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ForecastAnswers(FIVE_PER_WEEK, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ForecastAnswers(FIVE_PER_WEEK, WEEKLY_SEVEN, 0))
        .isInstanceOf(IllegalArgumentException.class);
    var answers = new ForecastAnswers(FIVE_PER_WEEK, WEEKLY_SEVEN);
    assertThatThrownBy(() -> answers.completionDate(1, START, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> answers.completionDate(1, START, 1.01))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> answers.completionDate(1, START, Double.NaN))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(answers.completionDate(5, START, 1)).contains(START.plusDays(7));
  }
}
