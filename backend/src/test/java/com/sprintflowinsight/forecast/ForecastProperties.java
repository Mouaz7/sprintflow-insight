package com.sprintflowinsight.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.SplittableRandom;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/** Invariants of the predictive distributions, scores and derived answers. */
class ForecastProperties {
  @Property(tries = 200)
  void poissonPmfSumsToOne(@ForAll @DoubleRange(min = 0, max = 2_000) double mean) {
    assertSumsToOne(DiscreteDistribution.poisson(mean));
    assertThat(DiscreteDistribution.poisson(mean).mean()).isCloseTo(mean, within(1e-6 * (1 + mean)));
  }

  @Property(tries = 200)
  void gammaPoissonPmfSumsToOne(
      @ForAll @DoubleRange(min = 0.05, max = 3_000) double shape,
      @ForAll @DoubleRange(min = 0.05, max = 300) double rate,
      @ForAll @DoubleRange(min = 0, max = 52) double periods) {
    DiscreteDistribution d = DiscreteDistribution.gammaPoissonPredictive(shape, rate, periods);
    assertSumsToOne(d);
    double mean = shape * periods / rate;
    assertThat(d.mean()).isCloseTo(mean, within(1e-6 * (1 + mean)));
  }

  @Property(tries = 200)
  void quantilesAreMonotoneInProbability(
      @ForAll @Size(min = 1, max = 30) List<@IntRange(min = 0, max = 40) Integer> counts,
      @ForAll @DoubleRange(min = 0, max = 1) double p1,
      @ForAll @DoubleRange(min = 0, max = 1) double p2,
      @ForAll @DoubleRange(min = 0.1, max = 8) double periods) {
    ThroughputHistory history = history(counts);
    for (ForecastModel model : ForecastModel.defaults(new SplittableRandom(counts.hashCode()))) {
      DiscreteDistribution d =
          model instanceof BootstrapMonteCarloModel
              ? new BootstrapMonteCarloModel(new SplittableRandom(1), 500).forecast(history, periods)
              : model.forecast(history, periods);
      assertSumsToOne(d);
      double low = Math.min(p1, p2);
      double high = Math.max(p1, p2);
      assertThat(d.quantile(low)).isLessThanOrEqualTo(d.quantile(high));
      assertThat(d.cdf(d.quantile(high))).isGreaterThanOrEqualTo(high - 1e-9);
    }
  }

  @Property(tries = 300)
  void crpsIsNonNegativeAndZeroOnlyForAPerfectPointForecast(
      @ForAll @Size(min = 1, max = 20) List<@IntRange(min = 0, max = 30) Integer> samples,
      @ForAll @IntRange(min = 0, max = 40) int observed) {
    DiscreteDistribution d =
        DiscreteDistribution.ofSamples(samples.stream().mapToInt(Integer::intValue).toArray());
    double crps = ForecastScores.crps(d, observed);
    assertThat(crps).isGreaterThanOrEqualTo(0);
    if (d.min() == d.max() && d.min() == observed) assertThat(crps).isZero();
    else assertThat(crps).isPositive();
    assertThat(ForecastScores.crps(DiscreteDistribution.pointMass(observed), observed)).isZero();
  }

  @Property(tries = 300)
  void conditionalPitIsADistributionFunction(
      @ForAll @DoubleRange(min = 0.1, max = 30) double mean,
      @ForAll @IntRange(min = 0, max = 60) int observed,
      @ForAll @DoubleRange(min = 0, max = 1) double u1,
      @ForAll @DoubleRange(min = 0, max = 1) double u2) {
    DiscreteDistribution d = DiscreteDistribution.poisson(mean);
    double low = ForecastScores.conditionalPit(d, observed, Math.min(u1, u2));
    double high = ForecastScores.conditionalPit(d, observed, Math.max(u1, u2));
    assertThat(low).isBetween(0.0, 1.0);
    assertThat(high).isBetween(low, 1.0);
    assertThat(ForecastScores.conditionalPit(d, observed, 1.0)).isEqualTo(1.0);
  }

  @Property(tries = 50)
  void bootstrapIsDeterministicWithAFixedSeed(
      @ForAll @Size(min = 1, max = 20) List<@IntRange(min = 0, max = 20) Integer> counts,
      @ForAll long seed,
      @ForAll @DoubleRange(min = 0, max = 6) double periods) {
    ThroughputHistory history = history(counts);
    DiscreteDistribution first =
        new BootstrapMonteCarloModel(new SplittableRandom(seed), 300).forecast(history, periods);
    DiscreteDistribution second =
        new BootstrapMonteCarloModel(new SplittableRandom(seed), 300).forecast(history, periods);
    assertThat(first.min()).isEqualTo(second.min());
    assertThat(first.max()).isEqualTo(second.max());
    for (int k = first.min(); k <= first.max(); k++) assertThat(first.pmf(k)).isEqualTo(second.pmf(k));
  }

  @Property(tries = 50)
  void completionDatesMeetTheirConfidenceAndAreOrdered(
      @ForAll @Size(min = 1, max = 20) List<@IntRange(min = 1, max = 15) Integer> counts,
      @ForAll @IntRange(min = 1, max = 120) int items) {
    var answers = new ForecastAnswers(new GammaPoissonModel(), history(counts));
    var start = java.time.LocalDate.of(2026, 1, 5);
    var dates = answers.completionDates(items, start);
    java.time.LocalDate previous = start;
    for (var entry : dates.entrySet()) {
      if (entry.getValue().isEmpty()) {
        // Too uncertain for this confidence within the five-year search window.
        assertThat(answers.probabilityToReach(items, ForecastAnswers.DEFAULT_MAX_DAYS))
            .isLessThan(entry.getKey());
        continue;
      }
      var date = entry.getValue().get();
      long days = start.until(date, java.time.temporal.ChronoUnit.DAYS);
      assertThat(answers.probabilityToReach(items, days)).isGreaterThanOrEqualTo(entry.getKey());
      assertThat(answers.probabilityToReach(items, days - 1)).isLessThan(entry.getKey());
      assertThat(date).isAfterOrEqualTo(previous);
      previous = date;
    }
  }

  private static ThroughputHistory history(List<Integer> counts) {
    return ThroughputHistory.weekly(counts.stream().mapToInt(Integer::intValue).toArray());
  }

  private static void assertSumsToOne(DiscreteDistribution d) {
    double sum = 0;
    for (int k = d.min(); k <= d.max(); k++) sum += d.pmf(k);
    assertThat(sum).isCloseTo(1, within(1e-9));
    assertThat(d.cdf(d.max())).isEqualTo(1.0);
    assertThat(d.cdf(d.min() - 1)).isZero();
  }
}
