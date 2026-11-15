package com.sprintflowinsight.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class LogisticRegressionTest {
  private static final LogisticRegression.Options NEARLY_UNREGULARISED =
      new LogisticRegression.Options(1e-6, 100, 1e-8);

  @Test
  void recoversTheCoefficientsOfTheGeneratingModel() {
    double[] truth = {1.5, -1.0, 0.5, 0.0};
    Fixtures.Data data = Fixtures.logistic(20_000, -0.5, truth, 42);

    LogisticRegression.Fit fit = LogisticRegression.fit(data.x(), data.y(), NEARLY_UNREGULARISED);

    assertThat(fit.converged()).isTrue();
    assertThat(fit.intercept()).isCloseTo(-0.5, within(0.06));
    double[] weights = fit.weights();
    for (int j = 0; j < truth.length; j++) {
      assertThat(weights[j]).as("weight %d", j).isCloseTo(truth[j], within(0.06));
    }
  }

  @Test
  void newtonConvergesInFewStepsToAVanishingGradient() {
    Fixtures.Data data = Fixtures.logistic(2_000, 0.3, new double[] {1, -2}, 7);

    LogisticRegression.Fit fit = LogisticRegression.fit(data.x(), data.y(), NEARLY_UNREGULARISED);

    assertThat(fit.converged()).isTrue();
    assertThat(fit.iterations()).isBetween(3, 20);
    assertThat(fit.gradientNorm()).isLessThanOrEqualTo(1e-8);
    double[] theta = {fit.intercept(), fit.weights()[0], fit.weights()[1]};
    double[] gradient = LogisticRegression.gradient(data.x(), data.y(), theta, 1e-6);
    for (double g : gradient) {
      assertThat(Math.abs(g)).isLessThanOrEqualTo(1e-8);
    }
  }

  @Test
  void strongerRegularisationGivesSmallerWeights() {
    Fixtures.Data data = Fixtures.logistic(1_000, 0, new double[] {2, -1, 1}, 3);
    double previous = Double.POSITIVE_INFINITY;
    for (double lambda : new double[] {0, 0.01, 0.1, 1, 10}) {
      var fit =
          LogisticRegression.fit(data.x(), data.y(), new LogisticRegression.Options(lambda, 100, 1e-9));
      double norm = Fixtures.norm(fit.weights());
      assertThat(norm).as("lambda %s", lambda).isLessThan(previous);
      previous = norm;
    }
  }

  @Test
  void veryStrongRegularisationLeavesOnlyTheBaseRate() {
    Fixtures.Data data = Fixtures.logistic(1_000, 0.8, new double[] {2, -1}, 5);
    int late = 0;
    for (boolean y : data.y()) {
      late += y ? 1 : 0;
    }
    double rate = late / 1000.0;

    var fit = LogisticRegression.fit(data.x(), data.y(), new LogisticRegression.Options(1e6, 100, 1e-12));

    assertThat(Fixtures.norm(fit.weights())).isLessThan(1e-5);
    assertThat(fit.intercept()).isCloseTo(Math.log(rate / (1 - rate)), within(1e-4));
  }

  @Test
  void trainingIsDeterministic() {
    Fixtures.Data data = Fixtures.logistic(500, 0.1, new double[] {1, 1, -1}, 11);

    var first = LogisticRegression.fit(data.x(), data.y(), LogisticRegression.Options.DEFAULT);
    var second = LogisticRegression.fit(data.x(), data.y(), LogisticRegression.Options.DEFAULT);

    assertThat(second.intercept()).isEqualTo(first.intercept());
    assertThat(second.weights()).containsExactly(first.weights());
    assertThat(second.iterations()).isEqualTo(first.iterations());
  }

  @Test
  void stopsAtTheIterationLimitWithoutClaimingConvergence() {
    Fixtures.Data data = Fixtures.logistic(500, 0.1, new double[] {3, -2}, 13);

    var fit = LogisticRegression.fit(data.x(), data.y(), new LogisticRegression.Options(0, 1, 1e-12));

    assertThat(fit.iterations()).isEqualTo(1);
    assertThat(fit.converged()).isFalse();
    assertThat(fit.gradientNorm()).isGreaterThan(1e-12);
  }

  @Test
  void anAlreadyOptimalStartTakesNoSteps() {
    double[][] x = {{0}, {0}};
    var fit = LogisticRegression.fit(x, new boolean[] {true, false}, LogisticRegression.Options.DEFAULT);

    assertThat(fit.iterations()).isZero();
    assertThat(fit.converged()).isTrue();
    assertThat(fit.intercept()).isZero();
  }

  @Test
  void collinearColumnsWithoutPenaltyStillReachTheSameFittedModel() {
    Fixtures.Data base = Fixtures.logistic(1_000, 0, new double[] {1}, 17);
    double[][] x = new double[base.x().length][];
    for (int i = 0; i < x.length; i++) {
      x[i] = new double[] {base.x()[i][0], base.x()[i][0]};
    }

    var options = new LogisticRegression.Options(0, 200, 1e-8);
    var fit = LogisticRegression.fit(x, base.y(), options);
    var single = LogisticRegression.fit(base.x(), base.y(), options);

    // The split between identical columns is not identified, but the fitted model is.
    assertThat(fit.gradientNorm()).isLessThan(1e-6);
    assertThat(fit.weights()[0] + fit.weights()[1]).isCloseTo(single.weights()[0], within(1e-4));
    assertThat(fit.intercept()).isCloseTo(single.intercept(), within(1e-4));
  }

  @Test
  void gradientMatchesFiniteDifferencesOfTheObjective() {
    Fixtures.Data data = Fixtures.logistic(200, 0.2, new double[] {1, -1}, 19);
    double[] theta = {0.3, -0.7, 0.4};
    double lambda = 0.5;
    double[] gradient = LogisticRegression.gradient(data.x(), data.y(), theta, lambda);
    double h = 1e-6;
    for (int j = 0; j < theta.length; j++) {
      double[] up = theta.clone();
      double[] down = theta.clone();
      up[j] += h;
      down[j] -= h;
      double numeric =
          (LogisticRegression.objective(data.x(), data.y(), up, lambda)
                  - LogisticRegression.objective(data.x(), data.y(), down, lambda))
              / (2 * h);
      assertThat(gradient[j]).as("component %d", j).isCloseTo(numeric, within(1e-6));
    }
  }

  @Test
  void objectiveIsMeanLogLossPlusHalfLambdaTimesSquaredWeights() {
    double[][] x = {{1}, {-2}};
    boolean[] y = {true, false};
    double[] theta = {0.5, 2};
    double z1 = 2.5;
    double z2 = -3.5;
    double expected =
        (Math.log(1 + Math.exp(z1)) - z1 + Math.log(1 + Math.exp(z2))) / 2 + 0.3 / 2 * 4;

    assertThat(LogisticRegression.objective(x, y, theta, 0.3)).isCloseTo(expected, within(1e-12));
  }

  @Test
  void sigmoidAndSoftplusAreStableAtExtremes() {
    assertThat(LogisticRegression.sigmoid(0)).isEqualTo(0.5);
    assertThat(LogisticRegression.sigmoid(2)).isCloseTo(1 / (1 + Math.exp(-2)), within(1e-15));
    assertThat(LogisticRegression.sigmoid(-2)).isCloseTo(1 / (1 + Math.exp(2)), within(1e-15));
    assertThat(LogisticRegression.sigmoid(-1000)).isZero();
    assertThat(LogisticRegression.sigmoid(1000)).isEqualTo(1.0);
    assertThat(LogisticRegression.softplus(0)).isCloseTo(Math.log(2), within(1e-15));
    assertThat(LogisticRegression.softplus(1000)).isEqualTo(1000);
    assertThat(LogisticRegression.softplus(-1000)).isZero();
    assertThat(LogisticRegression.softplus(-3)).isCloseTo(Math.log1p(Math.exp(-3)), within(1e-15));
  }

  @Test
  void rejectsInvalidInput() {
    var options = LogisticRegression.Options.DEFAULT;
    assertThatThrownBy(() -> LogisticRegression.fit(new double[][] {{1}}, new boolean[2], options))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LogisticRegression.fit(new double[0][], new boolean[0], options))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LogisticRegression.Options(-1, 10, 1e-6))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LogisticRegression.Options(Double.NaN, 10, 1e-6))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LogisticRegression.Options(Double.POSITIVE_INFINITY, 10, 1e-6))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LogisticRegression.Options(0, 0, 1e-6))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LogisticRegression.Options(0, 1, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new LogisticRegression.Options(0, 1, 1e-6).lambda()).isZero();
  }

  @Test
  void fitWeightsAreDefensiveCopies() {
    double[] weights = {1, 2};
    var fit = new LogisticRegression.Fit(0, weights, 1, true, 0);
    weights[0] = 9;
    fit.weights()[1] = 9;
    assertThat(fit.weights()).containsExactly(1, 2);
  }
}
