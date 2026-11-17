package com.sprintflowinsight.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiskModelTrainerTest {
  @Test
  void thresholdIsTenEventsPerFeatureInTheRarerClass() {
    assertThat(RiskModelTrainer.MIN_EVENTS_PER_CLASS).isEqualTo(80);
    assertThat(RiskModelTrainer.hasEnoughHistory(balanced(80, 500))).isTrue();
    assertThat(RiskModelTrainer.hasEnoughHistory(balanced(79, 500))).isFalse();
    assertThat(RiskModelTrainer.hasEnoughHistory(balanced(500, 79))).isFalse();
    assertThat(RiskModelTrainer.hasEnoughHistory(balanced(500, 80))).isTrue();
    assertThat(RiskModelTrainer.hasEnoughHistory(List.of())).isFalse();
  }

  @Test
  void coldStartUsesTheCalibratedHeuristic() {
    var examples = balanced(79, 500);
    var model = new RiskModelTrainer().train(examples);

    assertThat(model.source()).isEqualTo(RiskModel.Source.HEURISTIC);
    assertThat(model.intercept()).isEqualTo(ColdStartHeuristic.calibrated(examples).intercept());
  }

  @Test
  void enoughHistoryTrainsALogisticModelThatRanksTheFutureWell() {
    List<LabelledExample> history = Fixtures.history(4_000, 2026);
    var split = RiskEvaluation.timeSplit(history, 0.75);

    RiskModel model = new RiskModelTrainer().train(split.train());

    assertThat(model.source()).isEqualTo(RiskModel.Source.TRAINED);
    double[] scores = new double[split.test().size()];
    boolean[] late = new boolean[scores.length];
    for (int i = 0; i < scores.length; i++) {
      scores[i] = model.probability(split.test().get(i).features());
      late[i] = split.test().get(i).late();
    }
    assertThat(RiskEvaluation.auc(scores, late)).isGreaterThan(0.75);
    assertThat(RiskEvaluation.brier(scores, late)).isLessThan(0.2);
    // The strongest true effect (age vs p85) is the strongest learned standardised weight.
    double[] weights = model.weights();
    assertThat(weights[RiskFeature.AGE_IN_COLUMN_VS_P85.ordinal()]).isPositive();
    assertThat(weights[RiskFeature.REMAINING_SPRINT_DAYS.ordinal()]).isNegative();
    // Coefficients on the raw scale are the standardised ones divided by the feature scale.
    double[] scales = model.standardizer().scales();
    assertThat(weights[0] / scales[0]).isCloseTo(Fixtures.TRUE_WEIGHTS[0], within(0.2));
  }

  @Test
  void customOptionsAreUsed() {
    List<LabelledExample> history = Fixtures.history(2_000, 9);
    var strong = new RiskModelTrainer(new LogisticRegression.Options(100, 100, 1e-9)).train(history);
    var weak = new RiskModelTrainer().train(history);

    assertThat(Fixtures.norm(strong.weights())).isLessThan(Fixtures.norm(weak.weights()) / 10);
  }

  @Test
  void anUnconvergedFitFallsBackToTheHeuristic() {
    List<LabelledExample> history = Fixtures.history(2_000, 9);
    var hurried = new RiskModelTrainer(new LogisticRegression.Options(0.01, 1, 1e-12));

    RiskModel model = hurried.train(history);

    assertThat(RiskModelTrainer.hasEnoughHistory(history)).isTrue();
    assertThat(model.source()).isEqualTo(RiskModel.Source.HEURISTIC);
    assertThat(model.intercept()).isEqualTo(ColdStartHeuristic.calibrated(history).intercept());
  }

  private static List<LabelledExample> balanced(int late, int onTime) {
    List<LabelledExample> examples = new ArrayList<>();
    for (int i = 0; i < late + onTime; i++) {
      examples.add(new LabelledExample(Fixtures.reference(), i < late, Instant.EPOCH));
    }
    return examples;
  }
}
