package com.sprintflowinsight.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PercentilesTest {
  @Test
  void interpolatesBetweenOrderStatisticsLikeNumpyDefault() {
    // numpy.percentile([1, 2, 3, 4, 10], [50, 85, 95]) -> 3.0, 6.4, 8.8
    List<Double> values = List.of(10d, 1d, 4d, 2d, 3d);
    assertThat(Percentiles.of(values, 0.50)).isCloseTo(3.0, within(1e-12));
    assertThat(Percentiles.of(values, 0.85)).isCloseTo(6.4, within(1e-12));
    assertThat(Percentiles.of(values, 0.95)).isCloseTo(8.8, within(1e-12));
    assertThat(Percentiles.of(values, 0)).isEqualTo(1);
    assertThat(Percentiles.of(values, 1)).isEqualTo(10);
  }

  @Test
  void aSingleValueIsEveryPercentile() {
    assertThat(Percentiles.of(List.of(7d), 0.85)).isEqualTo(7);
  }

  @Test
  void sortsWithoutChangingTheInput() {
    List<Double> values = Arrays.asList(3d, 1d, 2d);
    assertThat(Percentiles.sorted(values)).containsExactly(1, 2, 3);
    assertThat(values).containsExactly(3d, 1d, 2d);
  }

  @Test
  void rejectsInvalidInput() {
    assertThatThrownBy(() -> Percentiles.of(List.of(), 0.5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Percentiles.of(List.of(1d), 1.01))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Percentiles.of(List.of(1d), -0.01))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Percentiles.of(List.of(1d), Double.NaN))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Percentiles.of(List.of(Double.NaN), 0.5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Percentiles.of(Arrays.asList(1d, null), 0.5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Percentiles.of(List.of(Double.POSITIVE_INFINITY), 0.5))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
