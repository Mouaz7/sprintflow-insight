package com.sprintflowinsight.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ColdStartHeuristicTest {
  @Test
  void withoutHistoryAReferenceTaskGetsThePriorRate() {
    var model = ColdStartHeuristic.calibrated(List.of());

    assertThat(model.source()).isEqualTo(RiskModel.Source.HEURISTIC);
    assertThat(model.probability(Fixtures.reference()))
        .isCloseTo(ColdStartHeuristic.PRIOR_LATE_RATE, within(1e-12));
    assertThat(model.predict(Fixtures.reference()).topReasons()).isEmpty();
  }

  @Test
  void interceptMakesTheMeanPredictionEqualTheShrunkObservedRate() {
    List<LabelledExample> examples = Fixtures.history(30, 5);
    long late = examples.stream().filter(LabelledExample::late).count();
    double target = (late + 1.0) / (examples.size() + 4.0);

    var model = ColdStartHeuristic.calibrated(examples);

    double mean =
        examples.stream().mapToDouble(e -> model.probability(e.features())).average().orElseThrow();
    assertThat(mean).isCloseTo(target, within(1e-9));
  }

  @Test
  void calibrationFollowsTheObservedRate() {
    List<LabelledExample> allLate = new ArrayList<>();
    List<LabelledExample> noneLate = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      allLate.add(new LabelledExample(Fixtures.reference(), true, Instant.EPOCH));
      noneLate.add(new LabelledExample(Fixtures.reference(), false, Instant.EPOCH));
    }

    assertThat(ColdStartHeuristic.calibrated(allLate).probability(Fixtures.reference()))
        .isCloseTo(21.0 / 24, within(1e-9));
    assertThat(ColdStartHeuristic.calibrated(noneLate).probability(Fixtures.reference()))
        .isCloseTo(1.0 / 24, within(1e-9));
  }

  @Test
  void expertWeightsPointInTheExpectedDirection() {
    var model = ColdStartHeuristic.calibrated(List.of());
    double base = model.probability(Fixtures.reference());

    assertThat(model.probability(new RiskFeatures(3, 0, 0, 0, 3, 0, 5, 3))).isGreaterThan(base);
    assertThat(model.probability(new RiskFeatures(1, 2, 0, 0, 3, 0, 5, 3))).isGreaterThan(base);
    assertThat(model.probability(new RiskFeatures(1, 0, 2, 0, 3, 0, 5, 3))).isGreaterThan(base);
    assertThat(model.probability(new RiskFeatures(1, 0, 0, 9, 3, 0, 5, 3))).isGreaterThan(base);
    assertThat(model.probability(new RiskFeatures(1, 0, 0, 0, 8, 0, 5, 3))).isGreaterThan(base);
    assertThat(model.probability(new RiskFeatures(1, 0, 0, 0, 3, 2, 5, 3))).isGreaterThan(base);
    assertThat(model.probability(new RiskFeatures(1, 0, 0, 0, 3, 0, 1, 3))).isGreaterThan(base);
    assertThat(model.probability(new RiskFeatures(1, 0, 0, 0, 3, 0, 5, 9))).isGreaterThan(base);
    assertThat(model.probability(new RiskFeatures(1, 0, 0, 0, 3, 0, 10, 3))).isLessThan(base);

    var reasons = model.predict(new RiskFeatures(4, 0, 0, 0, 3, 0, 0, 3)).topReasons();
    assertThat(reasons)
        .extracting(RiskPrediction.Contribution::feature)
        .containsExactly(RiskFeature.AGE_IN_COLUMN_VS_P85, RiskFeature.REMAINING_SPRINT_DAYS);
    assertThat(reasons.get(0).logOdds()).isCloseTo(3.0, within(1e-12));
    assertThat(reasons.get(1).logOdds()).isCloseTo(0.8 * 5 / 3, within(1e-12));
  }

  @Test
  void logitInvertsTheSigmoid() {
    assertThat(LogisticRegression.sigmoid(ColdStartHeuristic.logit(0.3))).isCloseTo(0.3, within(1e-15));
    assertThat(ColdStartHeuristic.logit(0.5)).isZero();
  }
}
