package com.sprintflowinsight.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class StandardizerTest {
  @Test
  void fitsMeansAndPopulationStandardDeviations() {
    var standardizer = Standardizer.fit(new double[][] {{1, 10}, {3, 10}, {5, 10}});

    assertThat(standardizer.means()).containsExactly(3, 10);
    assertThat(standardizer.scales()[0]).isCloseTo(Math.sqrt(8.0 / 3), within(1e-12));
    assertThat(standardizer.scales()[1]).isEqualTo(1.0);
    double[] z = standardizer.transform(new double[] {5, 10});
    assertThat(z[0]).isCloseTo(2 / Math.sqrt(8.0 / 3), within(1e-12));
    assertThat(z[1]).isZero();
  }

  @Test
  void transformedTrainingDataHasZeroMeanAndUnitVariance() {
    double[][] rows = Fixtures.logistic(300, 0, new double[] {1, 1}, 1).x();
    for (double[] row : rows) {
      row[1] = row[1] * 7 + 20;
    }
    double[][] z = Standardizer.fit(rows).transform(rows);
    for (int j = 0; j < 2; j++) {
      double mean = 0;
      double square = 0;
      for (double[] row : z) {
        mean += row[j];
        square += row[j] * row[j];
      }
      assertThat(mean / z.length).isCloseTo(0, within(1e-12));
      assertThat(square / z.length).isCloseTo(1, within(1e-12));
    }
  }

  @Test
  void fixedScalesAreValidatedAndCopied() {
    double[] means = {1, 2};
    double[] scales = {2, 4};
    var standardizer = Standardizer.of(means, scales);
    means[0] = 100;
    scales[0] = 100;
    standardizer.means()[1] = 100;
    standardizer.scales()[1] = 100;

    assertThat(standardizer.transform(new double[] {5, 6})).containsExactly(2, 1);
    assertThatThrownBy(() -> Standardizer.of(new double[] {1}, new double[] {1, 2}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Standardizer.of(new double[] {1}, new double[] {0}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Standardizer.of(new double[] {1}, new double[] {Double.NaN}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> Standardizer.of(new double[] {1}, new double[] {Double.POSITIVE_INFINITY}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> standardizer.transform(new double[] {1}))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
