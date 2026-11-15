package com.sprintflowinsight.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.Tuple.Tuple2;

/** Invariants of AUC, Brier score and calibration bins on arbitrary labelled scores. */
class RiskEvaluationProperties {
  @Property(tries = 500)
  void aucEqualsTheShareOfCorrectlyOrderedPairs(@ForAll("labelled") Tuple2<double[], boolean[]> d) {
    double[] s = d.get1();
    boolean[] y = d.get2();
    double wins = 0;
    long pairs = 0;
    for (int i = 0; i < s.length; i++) {
      for (int j = 0; j < s.length; j++) {
        if (y[i] && !y[j]) {
          pairs++;
          wins += s[i] > s[j] ? 1 : s[i] == s[j] ? 0.5 : 0;
        }
      }
    }
    assertThat(RiskEvaluation.auc(s, y)).isCloseTo(wins / pairs, within(1e-12));
  }

  @Property(tries = 500)
  void perfectRankersScoreOneAndReversedRankersZero(@ForAll("labelled") Tuple2<double[], boolean[]> d) {
    boolean[] y = d.get2();
    double[] perfect = new double[y.length];
    double[] reversed = new double[y.length];
    for (int i = 0; i < y.length; i++) {
      // Distinct within a class, but every positive above every negative.
      perfect[i] = (y[i] ? 10 : 0) + d.get1()[i];
      reversed[i] = -perfect[i];
    }
    assertThat(RiskEvaluation.auc(perfect, y)).isEqualTo(1.0);
    assertThat(RiskEvaluation.auc(reversed, y)).isEqualTo(0.0);
  }

  @Property(tries = 500)
  void aucIsInvariantUnderStrictlyIncreasingTransforms(
      @ForAll("labelled") Tuple2<double[], boolean[]> d) {
    double[] s = d.get1();
    double[] cubed = new double[s.length];
    double[] exponential = new double[s.length];
    double[] affine = new double[s.length];
    for (int i = 0; i < s.length; i++) {
      double centred = s[i] - 0.5;
      cubed[i] = centred * centred * centred + centred;
      exponential[i] = Math.exp(s[i]);
      affine[i] = 3 * s[i] - 7;
    }
    double auc = RiskEvaluation.auc(s, d.get2());
    assertThat(auc).isBetween(0.0, 1.0);
    assertThat(RiskEvaluation.auc(cubed, d.get2())).isCloseTo(auc, within(1e-12));
    assertThat(RiskEvaluation.auc(exponential, d.get2())).isCloseTo(auc, within(1e-12));
    assertThat(RiskEvaluation.auc(affine, d.get2())).isCloseTo(auc, within(1e-12));
  }

  @Property(tries = 500)
  void brierIsZeroForPerfectPredictionsAndAtMostOne(
      @ForAll("labelled") Tuple2<double[], boolean[]> d) {
    boolean[] y = d.get2();
    double[] perfect = new double[y.length];
    for (int i = 0; i < y.length; i++) {
      perfect[i] = y[i] ? 1 : 0;
    }
    assertThat(RiskEvaluation.brier(perfect, y)).isZero();
    assertThat(RiskEvaluation.brier(d.get1(), y)).isBetween(0.0, 1.0);
  }

  @Property(tries = 300)
  void calibrationBinsPartitionThePredictions(
      @ForAll("labelled") Tuple2<double[], boolean[]> d, @ForAll("binCounts") int bins) {
    assertBinsPartition(d, bins);
  }

  /** Replays the CI seed that exposed a rounded bin mean below its lower bound. */
  @Property(tries = 300, seed = "-3294350553674341294")
  void calibrationBinsPartitionThePredictionsForTheRegressionSeed(
      @ForAll("labelled") Tuple2<double[], boolean[]> d, @ForAll("binCounts") int bins) {
    assertBinsPartition(d, bins);
  }

  private static void assertBinsPartition(Tuple2<double[], boolean[]> d, int bins) {
    var curve = RiskEvaluation.calibrationCurve(d.get1(), d.get2(), bins);
    assertThat(curve.stream().mapToInt(RiskEvaluation.CalibrationBin::count).sum())
        .isEqualTo(d.get1().length);
    for (var bin : curve) {
      assertThat(bin.meanPredicted()).isBetween(bin.lower(), bin.upper());
      assertThat(bin.observedRate()).isBetween(0.0, 1.0);
    }
  }

  @Provide
  Arbitrary<Integer> binCounts() {
    return Arbitraries.integers().between(1, 20);
  }

  /** Scores in [0, 1] (with deliberate ties) and labels containing both classes. */
  @Provide
  Arbitrary<Tuple2<double[], boolean[]>> labelled() {
    Arbitrary<Double> score =
        Arbitraries.oneOf(
            Arbitraries.of(0.0, 0.25, 0.5, 1.0), Arbitraries.doubles().between(0, 1));
    return Arbitraries.integers()
        .between(2, 60)
        .flatMap(
            n ->
                Combinators.combine(
                        score.array(double[].class).ofSize(n),
                        Arbitraries.of(true, false).list().ofSize(n))
                    .as(Tuple::of))
        .filter(t -> t.get2().contains(true) && t.get2().contains(false))
        .map(t -> Tuple.of(t.get1(), toArray(t.get2())));
  }

  private static boolean[] toArray(List<Boolean> values) {
    boolean[] a = new boolean[values.size()];
    for (int i = 0; i < a.length; i++) {
      a[i] = values.get(i);
    }
    return a;
  }
}
