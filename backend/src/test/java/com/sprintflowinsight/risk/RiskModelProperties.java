package com.sprintflowinsight.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.LongRange;

/** Invariants of the risk model's predictions, explanations and training. */
class RiskModelProperties {
  private static final RiskModel TRAINED = new RiskModelTrainer().train(Fixtures.history(3_000, 77));
  private static final RiskModel HEURISTIC = ColdStartHeuristic.calibrated(List.of());

  @Property(tries = 500)
  void probabilitiesAreStrictlyBetweenZeroAndOne(@ForAll("tasks") RiskFeatures task) {
    for (RiskModel model : List.of(TRAINED, HEURISTIC)) {
      double p = model.probability(task);
      assertThat(p).isGreaterThan(0).isLessThan(1);
    }
  }

  @Property(tries = 500)
  void explanationsAreTheLargestTermsOfTheLogOdds(@ForAll("tasks") RiskFeatures task) {
    for (RiskModel model : List.of(TRAINED, HEURISTIC)) {
      var reasons = model.predict(task).topReasons();
      double[] all = model.contributions(task);
      assertThat(reasons).hasSizeLessThanOrEqualTo(RiskModel.TOP_REASONS);
      for (int i = 1; i < reasons.size(); i++) {
        assertThat(Math.abs(reasons.get(i - 1).logOdds()))
            .isGreaterThanOrEqualTo(Math.abs(reasons.get(i).logOdds()));
      }
      double smallestShown =
          reasons.stream().mapToDouble(r -> Math.abs(r.logOdds())).min().orElse(0);
      long larger =
          Arrays.stream(all).filter(c -> Math.abs(c) > smallestShown).count();
      assertThat(larger).isLessThanOrEqualTo(reasons.size());
      double logOdds = model.intercept();
      for (double c : all) {
        logOdds += c;
      }
      double expected = RiskModel.clamp(LogisticRegression.sigmoid(logOdds));
      assertThat(model.probability(task)).isCloseTo(expected, within(1e-12));
    }
  }

  @Property(tries = 40)
  void regularisationNeverIncreasesTheWeightNorm(
      @ForAll @LongRange(min = 0, max = 1_000_000) long seed,
      @ForAll @DoubleRange(min = 0.01, max = 1) double lambda,
      @ForAll @DoubleRange(min = 1.5, max = 20) double factor) {
    Fixtures.Data data = Fixtures.logistic(150, 0.2, new double[] {1.5, -1, 0.5}, seed);
    var weak = LogisticRegression.fit(data.x(), data.y(), new LogisticRegression.Options(lambda, 100, 1e-8));
    var strong =
        LogisticRegression.fit(
            data.x(), data.y(), new LogisticRegression.Options(lambda * factor, 100, 1e-8));

    assertThat(weak.converged()).isTrue();
    assertThat(strong.converged()).isTrue();
    assertThat(Fixtures.norm(strong.weights())).isLessThanOrEqualTo(Fixtures.norm(weak.weights()) + 1e-9);
  }

  @Property(tries = 20)
  void trainingIsDeterministic(@ForAll @LongRange(min = 0, max = 1_000_000) long seed) {
    var history = Fixtures.history(800, seed);
    var first = new RiskModelTrainer().train(history);
    var second = new RiskModelTrainer().train(history);

    assertThat(second.source()).isEqualTo(first.source());
    assertThat(second.intercept()).isEqualTo(first.intercept());
    assertThat(second.weights()).containsExactly(first.weights());
  }

  @Provide
  Arbitrary<RiskFeatures> tasks() {
    Arbitrary<Double> ratio = Arbitraries.doubles().between(0, 1e6);
    Arbitrary<Integer> count = Arbitraries.integers().between(0, 100_000);
    Arbitrary<Double> amount = Arbitraries.doubles().between(0, 1e6);
    return Combinators.combine(ratio, count, count, count, amount, count, amount, count)
        .as(RiskFeatures::new);
  }
}
