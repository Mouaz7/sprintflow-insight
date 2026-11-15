package com.sprintflowinsight.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class BacktesterTest {
  /** Point forecast of the latest period's count times the horizon, plus a fixed bias. */
  private record Naive(String name, int bias) implements ForecastModel {
    public DiscreteDistribution forecast(ThroughputHistory history, double periods) {
      return DiscreteDistribution.pointMass(
          (int) (history.count(history.size() - 1) * periods) + bias);
    }
  }

  private static ThroughputHistory constant(int value, int periods) {
    int[] counts = new int[periods];
    Arrays.fill(counts, value);
    return ThroughputHistory.weekly(counts);
  }

  @Test
  void configCountsOriginsAndTheRequiredHistory() {
    BacktestConfig config = BacktestConfig.DEFAULT;
    assertThat(config).isEqualTo(new BacktestConfig(8, 2, 6, 10));
    assertThat(config.origins(0)).isZero();
    assertThat(config.origins(9)).isZero();
    assertThat(config.origins(10)).isEqualTo(1);
    assertThat(config.origins(20)).isEqualTo(11);
    assertThat(config.requiredPeriods()).isEqualTo(15);
    assertThat(config.origins(config.requiredPeriods())).isEqualTo(config.minOrigins());
    assertThatThrownBy(() -> new BacktestConfig(0, 1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BacktestConfig(1, 0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BacktestConfig(1, 1, 0, 1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BacktestConfig(1, 1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThat(new BacktestConfig(1, 1, 1, 1).requiredPeriods()).isEqualTo(2);
  }

  @Test
  void aPerfectForecastScoresZeroAndCoversEverything() {
    BacktestReport report = new Backtester().evaluate(new Naive("perfect", 0), constant(4, 20));
    assertThat(report.model()).isEqualTo("perfect");
    assertThat(report.origins()).isEqualTo(11);
    assertThat(report.meanCrps()).isZero();
    assertThat(report.medianAbsoluteError()).isZero();
    assertThat(report.coverage()).containsOnlyKeys(0.5, 0.85, 0.95).allSatisfy((k, v) -> assertThat(v).isEqualTo(1.0));
    // A point mass at the observation spreads its PIT evenly.
    assertThat(report.pitHistogram()).hasSize(10).allSatisfy(bin -> assertThat(bin).isCloseTo(0.1, within(1e-12)));
  }

  @Test
  void aBiasedForecastIsPenalisedByItsError() {
    BacktestReport report = new Backtester().evaluate(new Naive("high", 3), constant(4, 20));
    assertThat(report.meanCrps()).isCloseTo(3, within(1e-12));
    assertThat(report.medianAbsoluteError()).isCloseTo(3, within(1e-12));
    assertThat(report.coverage().values()).containsOnly(0.0);
    // Observations always fall below the forecast: all PIT mass in the lowest bin.
    assertThat(report.pitHistogram().getFirst()).isEqualTo(1.0);
    assertThat(report.pitHistogram().subList(1, 10)).containsOnly(0.0);
    BacktestReport low = new Backtester().evaluate(new Naive("low", -2), constant(4, 20));
    assertThat(low.pitHistogram().getLast()).isEqualTo(1.0);
    assertThat(low.pitHistogram().subList(0, 9)).containsOnly(0.0);
  }

  @Test
  void eachOriginOnlySeesItsPast() {
    // 0..19: the naive forecast at origin o is 2(o−1) and the observation is o + (o+1).
    int[] counts = new int[20];
    for (int i = 0; i < 20; i++) counts[i] = i;
    BacktestReport report =
        new Backtester(new BacktestConfig(5, 2, 1, 4)).evaluate(new Naive("naive", 0), ThroughputHistory.weekly(counts));
    assertThat(report.origins()).isEqualTo(14);
    assertThat(report.medianAbsoluteError()).isCloseTo(3, within(1e-12));
    assertThat(report.meanCrps()).isCloseTo(3, within(1e-12));
    assertThat(report.pitHistogram()).hasSize(4);
  }

  @Test
  void evaluationNeedsAtLeastOneOrigin() {
    assertThatThrownBy(() -> new Backtester().evaluate(new Naive("n", 0), constant(1, 9)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Backtester(null)).isInstanceOf(IllegalArgumentException.class);
    assertThat(new Backtester().config()).isEqualTo(BacktestConfig.DEFAULT);
  }

  @Test
  void shortHistoriesAreReportedAsInsufficient() {
    var backtester = new Backtester();
    List<ForecastModel> candidates = List.of(new Naive("n", 0));
    assertThat(backtester.select(constant(3, 14), candidates))
        .isEqualTo(new ModelSelection.InsufficientHistory(14, 15));
    assertThat(backtester.select(constant(3, 15), candidates))
        .isInstanceOf(ModelSelection.Selected.class);
    assertThatThrownBy(() -> backtester.select(constant(3, 15), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void selectionPicksTheLowestMeanCrpsAndKeepsTheFirstOnTies() {
    var worse = new Naive("worse", 2);
    var best = new Naive("best", 0);
    var tied = new Naive("tied", 0);
    var selection = new Backtester().select(constant(5, 20), List.of(worse, best, tied, new Naive("bad", 1)));
    var selected = (ModelSelection.Selected) selection;
    assertThat(selected.model()).isSameAs(best);
    assertThat(selected.best().model()).isEqualTo("best");
    assertThat(selected.reports()).extracting(BacktestReport::model).containsExactly("worse", "best", "tied", "bad");
    var last = (ModelSelection.Selected) new Backtester().select(constant(5, 20), List.of(worse, best));
    assertThat(last.model()).isSameAs(best);
  }

  @Test
  void aCorrectlySpecifiedModelIsCalibratedOnPoissonData() {
    ThroughputHistory history = Sampling.poissonHistory(new SplittableRandom(11), 6, 400);
    var backtester = new Backtester(new BacktestConfig(8, 1, 6, 10));
    BacktestReport report = backtester.evaluate(new GammaPoissonModel(), history);
    assertThat(report.origins()).isEqualTo(392);
    // Discrete intervals hold at least their nominal mass, so allow a little over-coverage.
    assertThat(report.coverage().get(0.5)).isBetween(0.47, 0.66);
    assertThat(report.coverage().get(0.85)).isBetween(0.82, 0.95);
    assertThat(report.coverage().get(0.95)).isBetween(0.93, 1.0);
    // The non-randomised PIT is uniform for a calibrated count forecast.
    assertThat(report.pitHistogram()).allSatisfy(bin -> assertThat(bin).isBetween(0.06, 0.14));
    assertThat(report.pitHistogram().stream().mapToDouble(Double::doubleValue).sum())
        .isCloseTo(1, within(1e-9));
  }

  @Test
  void ewmaAdaptsToALevelShiftBetterThanTheBootstrap() {
    var random = new SplittableRandom(5);
    int[] counts = new int[60];
    for (int i = 0; i < 60; i++) counts[i] = Sampling.poisson(random, i < 30 ? 3 : 12);
    var history = ThroughputHistory.weekly(counts);
    var selection =
        (ModelSelection.Selected)
            new Backtester()
                .select(history, List.of(new BootstrapMonteCarloModel(new SplittableRandom(9), 2_000), new EwmaPoissonModel()));
    assertThat(selection.model().name()).isEqualTo("ewma-poisson");
    assertThat(selection.reports().get(1).meanCrps()).isLessThan(selection.reports().get(0).meanCrps());
  }

  @Test
  void backtestingIsReproducibleWithAFixedSeed() {
    ThroughputHistory history = Sampling.poissonHistory(new SplittableRandom(3), 4, 30);
    var first = new Backtester().evaluate(new BootstrapMonteCarloModel(new SplittableRandom(8), 1_000), history);
    var second = new Backtester().evaluate(new BootstrapMonteCarloModel(new SplittableRandom(8), 1_000), history);
    assertThat(first).isEqualTo(second);
  }
}
