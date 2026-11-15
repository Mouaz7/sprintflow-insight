package com.sprintflowinsight.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class ForecastScoresTest {
  private static final DiscreteDistribution QUARTERS =
      DiscreteDistribution.ofWeights(2, new double[] {0.25, 0.25, 0.25, 0.25}); // 2..5

  @Test
  void crpsOfAPointForecastIsTheAbsoluteError() {
    assertThat(ForecastScores.crps(DiscreteDistribution.pointMass(5), 5)).isZero();
    assertThat(ForecastScores.crps(DiscreteDistribution.pointMass(5), 8)).isCloseTo(3, within(1e-12));
    assertThat(ForecastScores.crps(DiscreteDistribution.pointMass(5), 1)).isCloseTo(4, within(1e-12));
  }

  @Test
  void crpsSumsSquaredCdfDifferencesOverTheIntegers() {
    // F = 0.25, 0.5, 0.75, 1 on 2..5 and y = 3: 0.0625 + 0.25 + 0.0625 + 0.
    assertThat(ForecastScores.crps(QUARTERS, 3)).isCloseTo(0.375, within(1e-12));
    // y = 0 below the support: 1 + 1 + 0.5625 + 0.25 + 0.0625.
    assertThat(ForecastScores.crps(QUARTERS, 0)).isCloseTo(2.875, within(1e-12));
    // y = 7 above the support: 0.0625 + 0.25 + 0.5625 + 1 + 1.
    assertThat(ForecastScores.crps(QUARTERS, 7)).isCloseTo(2.875, within(1e-12));
  }

  @Test
  void crpsEqualsTheEnergyForm() {
    // CRPS = E|X − y| − ½ E|X − X'|.
    DiscreteDistribution d = DiscreteDistribution.poisson(3.3);
    for (int y = 0; y <= 12; y++) {
      double first = 0;
      double second = 0;
      for (int i = d.min(); i <= d.max(); i++) {
        first += d.pmf(i) * Math.abs(i - y);
        for (int j = d.min(); j <= d.max(); j++) second += d.pmf(i) * d.pmf(j) * Math.abs(i - j);
      }
      assertThat(ForecastScores.crps(d, y)).as("y=%d", y).isCloseTo(first - second / 2, within(1e-10));
    }
  }

  @Test
  void centralIntervalsUseLowerAndUpperQuantiles() {
    // 50 %: [q(0.25), q(0.75)] = [2, 4]; 95 %: [q(0.025), q(0.975)] = [2, 5].
    assertThat(ForecastScores.covers(QUARTERS, 1, 0.5)).isFalse();
    assertThat(ForecastScores.covers(QUARTERS, 2, 0.5)).isTrue();
    assertThat(ForecastScores.covers(QUARTERS, 4, 0.5)).isTrue();
    assertThat(ForecastScores.covers(QUARTERS, 5, 0.5)).isFalse();
    assertThat(ForecastScores.covers(QUARTERS, 5, 0.95)).isTrue();
    assertThat(ForecastScores.covers(QUARTERS, 6, 0.95)).isFalse();
    DiscreteDistribution skewed = DiscreteDistribution.ofWeights(0, new double[] {0.5, 0.2, 0.3});
    // 50 %: [q(0.25), q(0.75)] = [0, 2]; q(0.75) moves to 2 because F(1) = 0.7.
    assertThat(ForecastScores.covers(skewed, 2, 0.5)).isTrue();
    DiscreteDistribution low = DiscreteDistribution.ofWeights(0, new double[] {0.2, 0.8});
    // 50 %: q(0.25) = 1, so zero is outside.
    assertThat(ForecastScores.covers(low, 0, 0.5)).isFalse();
    assertThatThrownBy(() -> ForecastScores.covers(QUARTERS, 3, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ForecastScores.covers(QUARTERS, 3, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void conditionalPitSpreadsTheObservationOverItsCdfJump() {
    // y = 3: F(2) = 0.25, F(3) = 0.5.
    assertThat(ForecastScores.conditionalPit(QUARTERS, 3, 0.1)).isZero();
    assertThat(ForecastScores.conditionalPit(QUARTERS, 3, 0.25)).isZero();
    assertThat(ForecastScores.conditionalPit(QUARTERS, 3, 0.3)).isCloseTo(0.2, within(1e-12));
    assertThat(ForecastScores.conditionalPit(QUARTERS, 3, 0.4)).isCloseTo(0.6, within(1e-12));
    assertThat(ForecastScores.conditionalPit(QUARTERS, 3, 0.5)).isEqualTo(1.0);
    assertThat(ForecastScores.conditionalPit(QUARTERS, 3, 0.9)).isEqualTo(1.0);
    // An observation outside the support is a step at F(y).
    assertThat(ForecastScores.conditionalPit(QUARTERS, 9, 0.99)).isZero();
    assertThat(ForecastScores.conditionalPit(QUARTERS, 9, 1.0)).isEqualTo(1.0);
    assertThat(ForecastScores.conditionalPit(QUARTERS, 0, 0.0)).isEqualTo(1.0);
    assertThat(ForecastScores.conditionalPit(QUARTERS, 0, 0.01)).isEqualTo(1.0);
  }
}
