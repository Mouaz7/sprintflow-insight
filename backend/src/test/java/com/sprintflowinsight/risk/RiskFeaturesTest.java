package com.sprintflowinsight.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RiskFeaturesTest {
  @Test
  void vectorFollowsTheFeatureEnumOrder() {
    var features = new RiskFeatures(1.5, 2, 3, 4, 5, 6, 7.5, 8);

    assertThat(features.toVector()).containsExactly(1.5, 2, 3, 4, 5, 6, 7.5, 8);
    assertThat(RiskFeature.COUNT).isEqualTo(8).isEqualTo(features.toVector().length);
  }

  @Test
  void containsNoPersonalIdentity() {
    String names =
        String.join(
            " ",
            Arrays.stream(RiskFeatures.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList());
    assertThat(names.toLowerCase())
        .doesNotContain("assignee", "user", "email", "name", "person", "reporter", "author");
  }

  @Test
  void rejectsNegativeOrNonFiniteValues() {
    assertThatThrownBy(() -> new RiskFeatures(-0.1, 0, 0, 0, 0, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RiskFeatures(Double.NaN, 0, 0, 0, 0, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RiskFeatures(Double.POSITIVE_INFINITY, 0, 0, 0, 0, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RiskFeatures(0, -1, 0, 0, 0, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RiskFeatures(0, 0, -1, 0, 0, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RiskFeatures(0, 0, 0, -1, 0, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RiskFeatures(0, 0, 0, 0, -1, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RiskFeatures(0, 0, 0, 0, 0, -1, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RiskFeatures(0, 0, 0, 0, 0, 0, -1, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RiskFeatures(0, 0, 0, 0, 0, 0, 0, -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new RiskFeatures(0, 0, 0, 0, 0, 0, 0, 0).toVector()).containsOnly(0);
  }

  @Test
  void percentileInterpolatesLinearlyBetweenOrderStatistics() {
    double[] values = {4, 1, 3, 2};

    assertThat(RiskFeatures.percentile(values, 0.85)).isCloseTo(3.55, within(1e-12));
    assertThat(RiskFeatures.percentile(values, 0.5)).isCloseTo(2.5, within(1e-12));
    assertThat(RiskFeatures.percentile(values, 0)).isEqualTo(1);
    assertThat(RiskFeatures.percentile(values, 1)).isEqualTo(4);
    assertThat(RiskFeatures.percentile(new double[] {7}, 0.85)).isEqualTo(7);
    assertThat(values).containsExactly(4, 1, 3, 2);
    assertThatThrownBy(() -> RiskFeatures.percentile(new double[0], 0.5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RiskFeatures.percentile(values, -0.01))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RiskFeatures.percentile(values, 1.01))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RiskFeatures.percentile(values, Double.NaN))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ageRatioComparesWithTheColumnsP85() {
    double[] history = {10, 20, 30, 40, 50};
    // p85 of history: position 3.4 -> 40 + 0.4 * 10 = 44
    assertThat(RiskFeatures.ageRatio(88, history)).isCloseTo(2.0, within(1e-12));
    assertThat(RiskFeatures.ageRatio(0, history)).isZero();
    assertThat(RiskFeatures.ageRatio(5, new double[0])).isEqualTo(1.0);
    // Columns passed within minutes are compared with one hour, not with almost zero.
    assertThat(RiskFeatures.ageRatio(3, new double[] {0.01, 0.02})).isEqualTo(3.0);
    assertThat(RiskFeatures.ageRatio(3, new double[] {1, 1})).isEqualTo(3.0);
    assertThatThrownBy(() -> RiskFeatures.ageRatio(-1, history))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
