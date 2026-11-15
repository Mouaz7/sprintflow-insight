package com.sprintflowinsight.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class RiskModelTest {
  private static final Standardizer IDENTITY =
      Standardizer.of(new double[RiskFeature.COUNT], new double[] {1, 1, 1, 1, 1, 1, 1, 1});

  @Test
  void explainsWithTheThreeLargestContributionsBySizeWithSign() {
    var model =
        new RiskModel(
            RiskModel.Source.TRAINED, IDENTITY, -1, new double[] {0.5, 1, -2, 0.1, 0, 3, 0, 0});

    RiskPrediction prediction = model.predict(new RiskFeatures(2, 1, 1, 1, 9, 1, 0, 0));

    assertThat(prediction.source()).isEqualTo(RiskModel.Source.TRAINED);
    assertThat(prediction.topReasons())
        .containsExactly(
            new RiskPrediction.Contribution(RiskFeature.DEPENDENCY_DEPTH, 3),
            new RiskPrediction.Contribution(RiskFeature.REASSIGNMENTS, -2),
            new RiskPrediction.Contribution(RiskFeature.AGE_IN_COLUMN_VS_P85, 1));
    assertThat(prediction.topReasons().get(0).raisesRisk()).isTrue();
    assertThat(prediction.topReasons().get(1).raisesRisk()).isFalse();
    // -1 + 1 + 1 - 2 + 0.1 + 3 = 2.1
    assertThat(prediction.probability())
        .isCloseTo(LogisticRegression.sigmoid(2.1), within(1e-12));
    assertThat(model.probability(new RiskFeatures(2, 1, 1, 1, 9, 1, 0, 0)))
        .isEqualTo(prediction.probability());
  }

  @Test
  void equalContributionsKeepFeatureOrderAndZeroContributionsAreNoReasons() {
    var model =
        new RiskModel(
            RiskModel.Source.HEURISTIC, IDENTITY, 0, new double[] {0, 1, 0, 0, 0, 0, 0, -1});

    var reasons = model.predict(new RiskFeatures(5, 2, 0, 0, 0, 0, 0, 2)).topReasons();

    assertThat(reasons)
        .extracting(RiskPrediction.Contribution::feature)
        .containsExactly(RiskFeature.TIMES_BLOCKED, RiskFeature.COLUMN_WIP);
    assertThat(model.predict(new RiskFeatures(0, 0, 0, 0, 0, 0, 0, 0)).topReasons()).isEmpty();
    assertThat(model.predict(new RiskFeatures(0, 0, 0, 0, 0, 0, 0, 0)).probability()).isEqualTo(0.5);
  }

  @Test
  void probabilitiesStayStrictlyInsideTheUnitInterval() {
    double[] huge = {1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000};
    var up = new RiskModel(RiskModel.Source.TRAINED, IDENTITY, 0, huge);
    var down = new RiskModel(RiskModel.Source.TRAINED, IDENTITY, -1e6, huge);
    var task = new RiskFeatures(10, 10, 10, 10, 10, 10, 10, 10);

    assertThat(up.probability(task)).isEqualTo(1 - RiskModel.EPSILON);
    assertThat(down.probability(task)).isEqualTo(RiskModel.EPSILON);
    assertThat(RiskModel.clamp(0.3)).isEqualTo(0.3);
  }

  @Test
  void exposesCopiesOfItsParameters() {
    double[] weights = {1, 0, 0, 0, 0, 0, 0, 0};
    var model = new RiskModel(RiskModel.Source.TRAINED, IDENTITY, 0.25, weights);
    weights[0] = 5;
    model.weights()[0] = 5;

    assertThat(model.weights()[0]).isEqualTo(1);
    assertThat(model.intercept()).isEqualTo(0.25);
    assertThat(model.source()).isEqualTo(RiskModel.Source.TRAINED);
    assertThat(model.standardizer()).isSameAs(IDENTITY);
  }

  @Test
  void rejectsParametersOfTheWrongDimension() {
    assertThatThrownBy(() -> new RiskModel(RiskModel.Source.TRAINED, IDENTITY, 0, new double[3]))
        .isInstanceOf(IllegalArgumentException.class);
    var narrow = Standardizer.of(new double[2], new double[] {1, 1});
    assertThatThrownBy(
            () -> new RiskModel(RiskModel.Source.TRAINED, narrow, 0, new double[RiskFeature.COUNT]))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void predictionReasonsAreImmutable() {
    var reasons = new java.util.ArrayList<RiskPrediction.Contribution>();
    var prediction = new RiskPrediction(0.5, RiskModel.Source.TRAINED, reasons);
    reasons.add(new RiskPrediction.Contribution(RiskFeature.STORY_POINTS, 1));

    assertThat(prediction.topReasons()).isEmpty();
    assertThatThrownBy(() -> prediction.topReasons().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(List.of(new RiskPrediction.Contribution(RiskFeature.STORY_POINTS, 0)))
        .noneMatch(RiskPrediction.Contribution::raisesRisk);
  }
}
