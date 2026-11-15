package com.sprintflowinsight.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class DiscreteDistributionTest {
  private static final double TIGHT = 1e-12;

  @Test
  void pointMassHasAllProbabilityOnOneValue() {
    DiscreteDistribution d = DiscreteDistribution.pointMass(4);
    assertThat(d.min()).isEqualTo(4);
    assertThat(d.max()).isEqualTo(4);
    assertThat(d.pmf(4)).isEqualTo(1.0);
    assertThat(d.pmf(3)).isZero();
    assertThat(d.pmf(5)).isZero();
    assertThat(d.cdf(3)).isZero();
    assertThat(d.cdf(4)).isEqualTo(1.0);
    assertThat(d.cdf(100)).isEqualTo(1.0);
    assertThat(d.quantile(0)).isEqualTo(4);
    assertThat(d.quantile(1)).isEqualTo(4);
    assertThat(d.median()).isEqualTo(4);
    assertThat(d.mean()).isEqualTo(4.0);
    assertThat(d.variance()).isZero();
    assertThat(d.probabilityAtLeast(4)).isEqualTo(1.0);
    assertThat(d.probabilityAtLeast(5)).isZero();
    assertThat(d.toString()).contains("4..4");
  }

  @Test
  void weightsAreNormalisedOnTheirOffset() {
    DiscreteDistribution d = DiscreteDistribution.ofWeights(2, new double[] {1, 0, 3});
    assertThat(d.min()).isEqualTo(2);
    assertThat(d.max()).isEqualTo(4);
    assertThat(d.pmf(2)).isCloseTo(0.25, within(TIGHT));
    assertThat(d.pmf(3)).isZero();
    assertThat(d.pmf(4)).isCloseTo(0.75, within(TIGHT));
    assertThat(d.cdf(1)).isZero();
    assertThat(d.cdf(2)).isCloseTo(0.25, within(TIGHT));
    assertThat(d.cdf(3)).isCloseTo(0.25, within(TIGHT));
    assertThat(d.cdf(4)).isEqualTo(1.0);
    assertThat(d.mean()).isCloseTo(3.5, within(TIGHT));
    assertThat(d.variance()).isCloseTo(0.25 * 2.25 + 0.75 * 0.25, within(TIGHT));
    assertThat(d.probabilityAtLeast(3)).isCloseTo(0.75, within(TIGHT));
  }

  @Test
  void quantileIsTheLeftContinuousInverse() {
    DiscreteDistribution d = DiscreteDistribution.ofWeights(0, new double[] {0.1, 0.2, 0.3, 0.4});
    assertThat(d.quantile(0)).isZero();
    assertThat(d.quantile(0.1)).isZero();
    assertThat(d.quantile(0.1000001)).isEqualTo(1);
    assertThat(d.quantile(0.3)).isEqualTo(1);
    assertThat(d.quantile(0.31)).isEqualTo(2);
    assertThat(d.quantile(0.6)).isEqualTo(2);
    assertThat(d.quantile(0.61)).isEqualTo(3);
    assertThat(d.quantile(1)).isEqualTo(3);
    assertThat(d.median()).isEqualTo(2);
  }

  @Test
  void quantileRejectsProbabilitiesOutsideTheUnitInterval() {
    DiscreteDistribution d = DiscreteDistribution.pointMass(0);
    assertThatThrownBy(() -> d.quantile(-0.01)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> d.quantile(1.01)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> d.quantile(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invalidWeightsAreRejected() {
    assertThatThrownBy(() -> DiscreteDistribution.ofWeights(0, new double[0]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DiscreteDistribution.ofWeights(-1, new double[] {1}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DiscreteDistribution.ofWeights(0, new double[] {1, -0.5}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DiscreteDistribution.ofWeights(0, new double[] {Double.NaN}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> DiscreteDistribution.ofWeights(0, new double[] {Double.POSITIVE_INFINITY}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DiscreteDistribution.ofWeights(0, new double[] {0, 0}))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void weightsAreCopied() {
    double[] weights = {1, 1};
    DiscreteDistribution d = DiscreteDistribution.ofWeights(0, weights);
    weights[0] = 100;
    assertThat(d.pmf(0)).isCloseTo(0.5, within(TIGHT));
  }

  @Test
  void samplesGiveTheEmpiricalDistribution() {
    DiscreteDistribution d = DiscreteDistribution.ofSamples(new int[] {3, 1, 1, 5});
    assertThat(d.min()).isEqualTo(1);
    assertThat(d.max()).isEqualTo(5);
    assertThat(d.pmf(1)).isCloseTo(0.5, within(TIGHT));
    assertThat(d.pmf(2)).isZero();
    assertThat(d.pmf(3)).isCloseTo(0.25, within(TIGHT));
    assertThat(d.pmf(5)).isCloseTo(0.25, within(TIGHT));
    assertThat(d.mean()).isCloseTo(2.5, within(TIGHT));
    assertThatThrownBy(() -> DiscreteDistribution.ofSamples(new int[0]))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void poissonMatchesItsClosedForm() {
    double mean = 2.5;
    DiscreteDistribution d = DiscreteDistribution.poisson(mean);
    double expected = Math.exp(-mean);
    for (int k = 0; k <= 15; k++) {
      assertThat(d.pmf(k)).as("pmf(%d)", k).isCloseTo(expected, within(1e-13));
      expected *= mean / (k + 1);
    }
    assertThat(d.min()).isZero();
    assertThat(d.mean()).isCloseTo(mean, within(1e-12));
    assertThat(d.variance()).isCloseTo(mean, within(1e-11));
    assertThat(d.median()).isEqualTo(2);
  }

  @Test
  void poissonWithLargeMeanNeitherUnderflowsNorLosesMass() {
    DiscreteDistribution d = DiscreteDistribution.poisson(5_000);
    assertThat(d.mean()).isCloseTo(5_000, within(1e-7));
    assertThat(d.variance()).isCloseTo(5_000, within(1e-5));
    assertThat(d.min()).isGreaterThan(4_000);
    assertThat(d.max()).isLessThan(6_000);
    assertThat(d.pmf(5_000)).isCloseTo(1 / Math.sqrt(2 * Math.PI * 5_000), within(1e-6));
  }

  @Test
  void poissonTailsAreTruncatedOnlyBelowTheTolerance() {
    DiscreteDistribution d = DiscreteDistribution.poisson(10);
    assertThat(d.max()).isBetween(35, 60);
    assertThat(d.pmf(d.max())).isLessThan(1e-15).isPositive();
    assertThat(d.pmf(0)).isCloseTo(Math.exp(-10), within(1e-16));
  }

  @Test
  void poissonOfZeroIsAPointMassAndInvalidMeansAreRejected() {
    assertThat(DiscreteDistribution.poisson(0).max()).isZero();
    assertThat(DiscreteDistribution.poisson(0).pmf(0)).isEqualTo(1.0);
    assertThatThrownBy(() -> DiscreteDistribution.poisson(-1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DiscreteDistribution.poisson(Double.NaN))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DiscreteDistribution.poisson(Double.POSITIVE_INFINITY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void negativeBinomialMatchesItsClosedForm() {
    for (double shape : new double[] {0.5, 1.0, 3.7, 40}) {
      double rate = 2.5;
      double periods = 3;
      DiscreteDistribution d = DiscreteDistribution.gammaPoissonPredictive(shape, rate, periods);
      double p = rate / (rate + periods);
      double q = 1 - p;
      double expected = Math.pow(p, shape);
      for (int k = 0; k <= 30; k++) {
        assertThat(d.pmf(k)).as("shape %s pmf(%d)", shape, k).isCloseTo(expected, within(1e-12));
        expected *= (k + shape) / (k + 1) * q;
      }
      assertThat(d.mean()).isCloseTo(shape * q / p, within(1e-9));
      assertThat(d.variance()).isCloseTo(shape * q / (p * p), within(1e-7));
    }
  }

  @Test
  void negativeBinomialWithLargeShapeDoesNotUnderflow() {
    DiscreteDistribution d = DiscreteDistribution.gammaPoissonPredictive(5_000, 100, 52);
    assertThat(d.mean()).isCloseTo(5_000 * 52 / 100.0, within(1e-6));
    assertThat(d.cdf(d.max())).isEqualTo(1.0);
  }

  @Test
  void negativeBinomialEdgeCasesAndValidation() {
    assertThat(DiscreteDistribution.gammaPoissonPredictive(2, 1, 0).max()).isZero();
    assertThatThrownBy(() -> DiscreteDistribution.gammaPoissonPredictive(0, 1, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DiscreteDistribution.gammaPoissonPredictive(1, 0, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DiscreteDistribution.gammaPoissonPredictive(Double.NaN, 1, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DiscreteDistribution.gammaPoissonPredictive(1, Double.NaN, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> DiscreteDistribution.gammaPoissonPredictive(Double.POSITIVE_INFINITY, 1, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> DiscreteDistribution.gammaPoissonPredictive(1, Double.POSITIVE_INFINITY, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DiscreteDistribution.gammaPoissonPredictive(1, 1, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void absurdlyWideDistributionsAreRejected() {
    assertThatThrownBy(() -> DiscreteDistribution.gammaPoissonPredictive(0.5, 1e-9, 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("too wide");
  }
}
