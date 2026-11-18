package com.sprintflowinsight.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.reporting.ForecastResponse.CompletionDate;
import com.sprintflowinsight.reporting.ForecastResponse.ItemsAtConfidence;
import com.sprintflowinsight.reporting.ThroughputDerivation.Derived;
import com.sprintflowinsight.reporting.ThroughputDerivation.Period;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/** Invariants of deriving histories from completions and answering forecasts from them. */
class ForecastWiringProperties {
  private static final LocalDate TODAY = LocalDate.parse("2026-11-18");
  private static final Instant START_OF_THIS_WEEK = Instant.parse("2026-11-16T00:00:00Z");

  @Property(tries = 300)
  void weeklyBinsCountEveryCompletionBeforeThisWeekExactlyOnce(
      @ForAll @Size(max = 60) List<@IntRange(min = 0, max = 400 * 24) Integer> hoursAgo) {
    List<Instant> completions =
        hoursAgo.stream().map(h -> TODAY.atTime(12, 0).toInstant(ZoneOffset.UTC).minus(h, ChronoUnit.HOURS)).toList();
    long beforeThisWeek =
        completions.stream().filter(at -> at.isBefore(START_OF_THIS_WEEK)).count();

    Derived derived = ThroughputDerivation.weekly(completions, TODAY);

    assertThat(derived.history().total(0, derived.history().size())).isEqualTo(beforeThisWeek);
    assertThat(derived.periods()).hasSize(derived.history().size());
    for (int i = 0; i < derived.periods().size(); i++) {
      Period period = derived.periods().get(i);
      assertThat(period.count()).isEqualTo(derived.history().count(i));
      assertThat(ChronoUnit.DAYS.between(period.start(), period.end())).isEqualTo(6);
      if (i > 0) assertThat(period.start()).isEqualTo(derived.periods().get(i - 1).end().plusDays(1));
    }
    if (!derived.periods().isEmpty()) {
      assertThat(derived.periods().get(derived.periods().size() - 1).end())
          .isEqualTo(LocalDate.parse("2026-11-15"));
      assertThat(derived.history().count(0)).as("history starts at the first completion").isPositive();
    }
  }

  @Property(tries = 40)
  void forecastsAreDeterministicAndMonotoneInConfidence(
      @ForAll @Size(min = 1, max = 18) List<@IntRange(min = 0, max = 12) Integer> counts,
      @ForAll @IntRange(min = 0, max = 200) int remaining,
      @ForAll @IntRange(min = 0, max = 120) int days,
      @ForAll @IntRange(min = 0, max = 80) int targetItems) {
    Derived derived = ForecastCalculatorTest.weekly(counts.stream().mapToInt(Integer::intValue).toArray());
    ForecastCalculator.Inputs inputs =
        ForecastCalculatorTest.inputs(
            derived, remaining, ForecastCalculatorTest.target(days, targetItems));

    ForecastResponse response = ForecastCalculator.calculate(inputs);

    assertThat(ForecastCalculator.calculate(inputs)).isEqualTo(response);
    if (response.model().status() == ForecastResponse.SelectionStatus.NO_HISTORY) {
      assertThat(response.completionDates()).isEmpty();
      return;
    }
    List<CompletionDate> dates = response.completionDates();
    for (int i = 1; i < dates.size(); i++) {
      LocalDate earlier = dates.get(i - 1).date();
      LocalDate later = dates.get(i).date();
      if (later != null) {
        assertThat(earlier).isNotNull();
        assertThat(later).isAfterOrEqualTo(earlier);
      }
    }
    List<ItemsAtConfidence> items = response.itemsByDate().items();
    for (int i = 1; i < items.size(); i++) {
      assertThat(items.get(i).items()).isLessThanOrEqualTo(items.get(i - 1).items());
    }
    assertThat(response.target().probability()).isBetween(0.0, 1.0 + 1e-9);
  }
}
