package com.sprintflowinsight.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class ForecastModelsTest {
  private static final ThroughputHistory HISTORY = ThroughputHistory.weekly(3, 7, 4, 0, 6);

  @Test
  void historyValidatesAndCopiesItsCounts() {
    int[] counts = {1, 2, 3};
    ThroughputHistory history = ThroughputHistory.weekly(counts);
    counts[0] = 99;
    assertThat(history.count(0)).isEqualTo(1);
    history.counts()[1] = 99;
    assertThat(history.count(1)).isEqualTo(2);
    assertThat(history.size()).isEqualTo(3);
    assertThat(history.periodDays()).isEqualTo(7);
    assertThat(history.unit()).isEqualTo(ThroughputUnit.ITEMS);
    assertThat(history.total(0, 3)).isEqualTo(6);
    assertThat(history.total(1, 3)).isEqualTo(5);
    assertThat(history.prefix(2)).isEqualTo(ThroughputHistory.weekly(1, 2));
    assertThat(history.periodsFor(14)).isEqualTo(2.0);
    assertThat(history.periodsFor(3)).isCloseTo(3 / 7.0, within(1e-15));
    assertThatThrownBy(() -> ThroughputHistory.weekly(1, -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ThroughputHistory.of(0, ThroughputUnit.ITEMS, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ThroughputHistory.of(7, null, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void velocityHistoriesUseSprintsAndPoints() {
    ThroughputHistory velocity = ThroughputHistory.sprintVelocity(14, 21, 34);
    assertThat(velocity.periodDays()).isEqualTo(14);
    assertThat(velocity.unit()).isEqualTo(ThroughputUnit.STORY_POINTS);
    assertThat(velocity.periodsFor(7)).isEqualTo(0.5);
    assertThat(velocity).isNotEqualTo(ThroughputHistory.of(14, ThroughputUnit.ITEMS, 21, 34));
    assertThat(velocity).isNotEqualTo(ThroughputHistory.of(7, ThroughputUnit.STORY_POINTS, 21, 34));
    assertThat(velocity).isNotEqualTo(ThroughputHistory.of(14, ThroughputUnit.STORY_POINTS, 21));
    assertThat(velocity).isNotEqualTo("21, 34");
    assertThat(velocity.hashCode())
        .isEqualTo(ThroughputHistory.of(14, ThroughputUnit.STORY_POINTS, 21, 34).hashCode())
        .isNotEqualTo(ThroughputHistory.of(14, ThroughputUnit.ITEMS, 21, 34).hashCode())
        .isNotEqualTo(ThroughputHistory.of(7, ThroughputUnit.STORY_POINTS, 21, 34).hashCode());
    assertThat(velocity.toString()).contains("[21, 34]", "14d", "STORY_POINTS");
  }

  @Test
  void bootstrapIsDeterministicForAFixedSeed() {
    var first = new BootstrapMonteCarloModel(new SplittableRandom(42), 2_000).forecast(HISTORY, 3);
    var second = new BootstrapMonteCarloModel(new SplittableRandom(42), 2_000).forecast(HISTORY, 3);
    for (int k = first.min(); k <= first.max(); k++) assertThat(first.pmf(k)).isEqualTo(second.pmf(k));
    assertThat(first.max()).isEqualTo(second.max());
  }

  @Test
  void bootstrapResamplesObservedPeriods() {
    var model = new BootstrapMonteCarloModel(new SplittableRandom(1));
    assertThat(model.name()).isEqualTo("bootstrap-monte-carlo");
    DiscreteDistribution constant = model.forecast(ThroughputHistory.weekly(3, 3, 3), 2);
    assertThat(constant.min()).isEqualTo(6);
    assertThat(constant.max()).isEqualTo(6);
    DiscreteDistribution twoWeeks = model.forecast(HISTORY, 2);
    assertThat(twoWeeks.mean()).isCloseTo(2 * 4.0, within(0.15));
    assertThat(twoWeeks.min()).isZero();
    assertThat(twoWeeks.max()).isEqualTo(14);
    assertThat(twoWeeks.pmf(1)).isZero();
    assertThat(model.forecast(HISTORY, 0).max()).isZero();
  }

  @Test
  void bootstrapThinsAFractionalPeriodBinomially() {
    var model = new BootstrapMonteCarloModel(new SplittableRandom(7), 20_000);
    DiscreteDistribution half = model.forecast(ThroughputHistory.weekly(4), 0.5);
    for (int k = 0; k <= 4; k++) {
      double binomial = choose(4, k) / 16.0;
      assertThat(half.pmf(k)).as("pmf(%d)", k).isCloseTo(binomial, within(0.015));
    }
    DiscreteDistribution oneAndHalf = model.forecast(ThroughputHistory.weekly(4), 1.5);
    assertThat(oneAndHalf.min()).isEqualTo(4);
    assertThat(oneAndHalf.mean()).isCloseTo(6, within(0.05));
  }

  @Test
  void bootstrapValidatesItsInputs() {
    assertThatThrownBy(() -> new BootstrapMonteCarloModel(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BootstrapMonteCarloModel(new SplittableRandom(), 0))
        .isInstanceOf(IllegalArgumentException.class);
    var model = new BootstrapMonteCarloModel(new SplittableRandom(), 10);
    assertThatThrownBy(() -> model.forecast(ThroughputHistory.weekly(), 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> model.forecast(HISTORY, -1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> model.forecast(HISTORY, Double.NaN))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> model.forecast(HISTORY, Double.POSITIVE_INFINITY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void bootstrapDefaultsToTenThousandTrials() {
    DiscreteDistribution d =
        new BootstrapMonteCarloModel(new SplittableRandom(3)).forecast(ThroughputHistory.weekly(0, 1), 1);
    // With 10 000 trials every probability is a multiple of 1/10 000.
    assertThat(d.pmf(1) * BootstrapMonteCarloModel.DEFAULT_TRIALS)
        .isCloseTo(Math.rint(d.pmf(1) * 10_000), within(1e-6));
    assertThat(d.pmf(1)).isCloseTo(0.5, within(0.02));
  }

  @Test
  void gammaPoissonUpdatesTheConjugatePosterior() {
    var model = new GammaPoissonModel(2, 0.5);
    assertThat(model.name()).isEqualTo("gamma-poisson");
    assertThat(model.posteriorShape(HISTORY)).isEqualTo(22.0);
    assertThat(model.posteriorRate(HISTORY)).isEqualTo(5.5);
    DiscreteDistribution forecast = model.forecast(HISTORY, 2);
    DiscreteDistribution expected = DiscreteDistribution.gammaPoissonPredictive(22, 5.5, 2);
    for (int k = 0; k <= 40; k++) assertThat(forecast.pmf(k)).isEqualTo(expected.pmf(k));
    assertThat(forecast.mean()).isCloseTo(22 / 5.5 * 2, within(1e-9));
  }

  @Test
  void gammaPoissonDefaultsToAWeakPriorAndAcceptsEmptyHistory() {
    var model = new GammaPoissonModel();
    assertThat(model.posteriorShape(ThroughputHistory.weekly())).isEqualTo(0.5);
    assertThat(model.posteriorRate(ThroughputHistory.weekly())).isEqualTo(0.5);
    assertThat(model.forecast(ThroughputHistory.weekly(), 1).mean()).isCloseTo(1, within(1e-9));
    // After twenty periods the data dominate the prior.
    int[] fives = new int[20];
    java.util.Arrays.fill(fives, 5);
    assertThat(model.forecast(ThroughputHistory.weekly(fives), 1).mean())
        .isCloseTo(100.5 / 20.5, within(1e-9));
  }

  @Test
  void gammaPoissonRejectsInvalidPriors() {
    assertThatThrownBy(() -> new GammaPoissonModel(0, 1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GammaPoissonModel(1, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GammaPoissonModel(Double.NaN, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GammaPoissonModel(Double.POSITIVE_INFINITY, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GammaPoissonModel(1, Double.POSITIVE_INFINITY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void gammaPoissonMatchesMonteCarloOfTheSameModel() {
    var model = new GammaPoissonModel(1, 0.2);
    ThroughputHistory history = ThroughputHistory.weekly(2, 5, 1, 4);
    double shape = model.posteriorShape(history);
    double rate = model.posteriorRate(history);
    double periods = 3;
    DiscreteDistribution exact = model.forecast(history, periods);
    SplittableRandom random = new SplittableRandom(2024);
    int trials = 100_000;
    int[] samples = new int[trials];
    for (int i = 0; i < trials; i++) {
      samples[i] = Sampling.poisson(random, Sampling.gamma(random, shape, rate) * periods);
    }
    DiscreteDistribution simulated = DiscreteDistribution.ofSamples(samples);
    for (int k = 0; k <= 40; k++) {
      assertThat(simulated.cdf(k)).as("cdf(%d)", k).isCloseTo(exact.cdf(k), within(0.006));
    }
    assertThat(simulated.mean()).isCloseTo(exact.mean(), within(0.06));
  }

  @Test
  void ewmaWeighsRecentPeriodsMore() {
    var model = new EwmaPoissonModel(1);
    assertThat(model.name()).isEqualTo("ewma-poisson");
    // Weights 1, 0.5, 0.25 from the newest period backwards.
    assertThat(model.rate(ThroughputHistory.weekly(8, 0, 4))).isCloseTo(6.0 / 1.75, within(1e-12));
    assertThat(model.rate(ThroughputHistory.weekly(4, 0, 8))).isCloseTo(9.0 / 1.75, within(1e-12));
    assertThat(new EwmaPoissonModel().rate(ThroughputHistory.weekly(5, 5, 5))).isCloseTo(5, within(1e-12));
    double defaultRate = new EwmaPoissonModel().rate(ThroughputHistory.weekly(0, 0, 0, 0, 16));
    // Default half-life of four periods: newest weight 1, the next ones 0.5^(1/4) per step.
    double d = Math.pow(0.5, 0.25);
    assertThat(defaultRate).isCloseTo(16 / (1 + d + d * d + d * d * d + d * d * d * d), within(1e-12));
  }

  @Test
  void ewmaForecastsAPoissonWithTheScaledRate() {
    var model = new EwmaPoissonModel(2);
    DiscreteDistribution forecast = model.forecast(HISTORY, 2.5);
    DiscreteDistribution expected = DiscreteDistribution.poisson(model.rate(HISTORY) * 2.5);
    for (int k = 0; k <= 40; k++) assertThat(forecast.pmf(k)).isEqualTo(expected.pmf(k));
    assertThat(model.forecast(ThroughputHistory.weekly(0, 0), 3).max()).isZero();
  }

  @Test
  void ewmaValidatesItsInputs() {
    assertThatThrownBy(() -> new EwmaPoissonModel(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EwmaPoissonModel(Double.NaN))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EwmaPoissonModel(Double.POSITIVE_INFINITY))
        .isInstanceOf(IllegalArgumentException.class);
    var model = new EwmaPoissonModel();
    assertThatThrownBy(() -> model.rate(ThroughputHistory.weekly()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> model.forecast(HISTORY, -0.5)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void defaultCandidatesAreTheThreeModelsInTieBreakingOrder() {
    assertThat(ForecastModel.defaults(new SplittableRandom(0)))
        .extracting(ForecastModel::name)
        .containsExactly("gamma-poisson", "ewma-poisson", "bootstrap-monte-carlo");
  }

  private static double choose(int n, int k) {
    double result = 1;
    for (int i = 1; i <= k; i++) result = result * (n - k + i) / i;
    return result;
  }
}
