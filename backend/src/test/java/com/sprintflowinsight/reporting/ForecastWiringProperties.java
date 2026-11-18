package com.sprintflowinsight.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.forecast.ThroughputHistory;
import com.sprintflowinsight.reporting.ThroughputDerivation.Derived;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

class ForecastWiringProperties {
  private static final LocalDate TODAY = LocalDate.parse("2026-11-18");
  private static final Instant WEEK = Instant.parse("2026-11-16T00:00:00Z");

  @Property(tries = 150, seed = "20261118061")
  void weeklyBinsMatchAnIndependentCalendarLedger(
      @ForAll @Size(max = 60) List<@IntRange(min = 0, max = 9600) Integer> hoursAgo) {
    var completed =
        hoursAgo.stream()
            .map(h -> TODAY.atTime(12, 0).toInstant(ZoneOffset.UTC).minus(h, ChronoUnit.HOURS))
            .toList();
    Derived derived = ThroughputDerivation.weekly(completed, TODAY);
    assertThat(derived.history().total(0, derived.history().size()))
        .isEqualTo(completed.stream().filter(at -> at.isBefore(WEEK)).count());
    for (int i = 0; i < derived.periods().size(); i++) {
      var period = derived.periods().get(i);
      long actual =
          completed.stream()
              .filter(
                  at ->
                      !at.isBefore(period.start().atStartOfDay(ZoneOffset.UTC).toInstant())
                          && at.isBefore(
                              period.end().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()))
              .count();
      assertThat(period.count()).isEqualTo(actual);
    }
  }

  @Property(tries = 20, seed = "20261118062")
  void constantCapacityMatchesClosedFormAnswers(
      @ForAll @IntRange(min = 1, max = 20) int perWeek,
      @ForAll @IntRange(min = 0, max = 100) int remaining,
      @ForAll @IntRange(min = 0, max = 56) int days,
      @ForAll @IntRange(min = 0, max = 100) int target) {
    var history = ThroughputHistory.weekly(perWeek, perWeek, perWeek, perWeek, perWeek, perWeek);
    var result =
        ForecastCalculator.calculate(
            ForecastCalculatorTest.inputs(
                history, remaining, days, target, ForecastCalculatorTest.SCOPE));
    long deliveryDays = (remaining * 7L + perWeek - 1) / perWeek;
    long delivered = perWeek * (long) days / 7;
    assertThat(result.completionDates())
        .allMatch(row -> row.date().equals(TODAY.plusDays(deliveryDays)));
    assertThat(result.itemsByDate().items()).allMatch(row -> row.items() == delivered);
    assertThat(result.target().probability()).isEqualTo(delivered >= target ? 1.0 : 0.0);
  }
}
