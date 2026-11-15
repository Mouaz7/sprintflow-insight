package com.sprintflowinsight.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiskEvaluationTest {
  @Test
  void aucCountsTiedPairsAsHalf() {
    // Pairs (pos, neg): (0.8,0.1)=1 (0.8,0.4)=1 (0.4,0.1)=1 (0.4,0.4)=0.5 -> 3.5/4
    double[] scores = {0.1, 0.4, 0.4, 0.8};
    boolean[] positive = {false, false, true, true};

    assertThat(RiskEvaluation.auc(scores, positive)).isCloseTo(0.875, within(1e-12));
    assertThat(RiskEvaluation.auc(new double[] {1, 1, 1}, new boolean[] {true, false, false}))
        .isEqualTo(0.5);
    assertThat(RiskEvaluation.auc(new double[] {0.9, 0.1}, new boolean[] {true, false})).isEqualTo(1);
    assertThat(RiskEvaluation.auc(new double[] {0.1, 0.9}, new boolean[] {true, false})).isZero();
  }

  @Test
  void aucNeedsBothClassesAndFiniteScores() {
    assertThatThrownBy(() -> RiskEvaluation.auc(new double[] {1, 2}, new boolean[] {true, true}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RiskEvaluation.auc(new double[] {1, 2}, new boolean[] {false, false}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> RiskEvaluation.auc(new double[] {1, Double.NaN}, new boolean[] {true, false}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RiskEvaluation.auc(new double[] {1, 2}, new boolean[] {true, false, true}))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void brierIsTheMeanSquaredError() {
    assertThat(RiskEvaluation.brier(new double[] {1, 0}, new boolean[] {true, false})).isZero();
    assertThat(RiskEvaluation.brier(new double[] {0.5, 0.5}, new boolean[] {true, false}))
        .isEqualTo(0.25);
    assertThat(RiskEvaluation.brier(new double[] {0.8, 0.3}, new boolean[] {true, false}))
        .isCloseTo((0.04 + 0.09) / 2, within(1e-12));
    assertThat(RiskEvaluation.brier(new double[] {0, 1}, new boolean[] {true, false})).isEqualTo(1);
    assertThatThrownBy(() -> RiskEvaluation.brier(new double[0], new boolean[0]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RiskEvaluation.brier(new double[] {1.1}, new boolean[] {true}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RiskEvaluation.brier(new double[] {-0.1}, new boolean[] {true}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RiskEvaluation.brier(new double[] {Double.NaN}, new boolean[] {true}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RiskEvaluation.brier(new double[] {0.5}, new boolean[0]))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void calibrationCurveGroupsIntoEqualWidthBins() {
    double[] p = {0.05, 0.15, 0.12, 0.95, 1.0, 0.5};
    boolean[] y = {false, true, false, true, true, false};

    var curve = RiskEvaluation.calibrationCurve(p, y, 10);

    assertThat(curve).hasSize(4);
    var second = curve.get(1);
    assertThat(second.lower()).isCloseTo(0.1, within(1e-12));
    assertThat(second.upper()).isCloseTo(0.2, within(1e-12));
    assertThat(second.count()).isEqualTo(2);
    assertThat(second.meanPredicted()).isCloseTo(0.135, within(1e-12));
    assertThat(second.observedRate()).isEqualTo(0.5);
    assertThat(curve.get(0)).isEqualTo(new RiskEvaluation.CalibrationBin(0, 0.1, 1, 0.05, 0));
    assertThat(curve.get(2).lower()).isEqualTo(0.5);
    var last = curve.get(3);
    assertThat(last.upper()).isEqualTo(1.0);
    assertThat(last.count()).isEqualTo(2);
    assertThat(last.observedRate()).isEqualTo(1.0);
    assertThat(RiskEvaluation.calibrationCurve(new double[] {0.3}, new boolean[] {true}, 1))
        .containsExactly(new RiskEvaluation.CalibrationBin(0, 1, 1, 0.3, 1));
    assertThat(RiskEvaluation.calibrationCurve(new double[0], new boolean[0], 5)).isEmpty();
    assertThatThrownBy(() -> RiskEvaluation.calibrationCurve(p, y, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RiskEvaluation.calibrationCurve(new double[] {2}, new boolean[] {true}, 3))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RiskEvaluation.calibrationCurve(p, new boolean[1], 3))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void binBoundariesAreExactForDecimalProbabilities() {
    assertThat(RiskEvaluation.binOf(0.7, 10)).isEqualTo(7);
    assertThat(RiskEvaluation.binOf(Math.nextDown(0.7), 10)).isEqualTo(6);
    assertThat(RiskEvaluation.binOf(0.3, 10)).isEqualTo(3);
    assertThat(RiskEvaluation.binOf(Math.nextDown(0.3), 10)).isEqualTo(2);
    assertThat(RiskEvaluation.binOf(1.0, 10)).isEqualTo(9);
    assertThat(RiskEvaluation.binOf(0.0, 10)).isZero();
    assertThat(RiskEvaluation.binOf(Math.nextDown(1.0), 10)).isEqualTo(9);
    assertThat(RiskEvaluation.binOf(0.5, 1)).isZero();
    for (int bins = 1; bins <= 50; bins++) {
      for (int b = 0; b < bins; b++) {
        double lower = (double) b / bins;
        assertThat(RiskEvaluation.binOf(lower, bins)).isEqualTo(b);
        if (b > 0) {
          assertThat(RiskEvaluation.binOf(Math.nextDown(lower), bins)).isEqualTo(b - 1);
        }
      }
    }
  }

  @Test
  void meanPredictionStaysInsideItsBinDespiteRounding() {
    // Shrunk counterexample from CI (seed -3294350553674341294): 35 × 0.0 and 3 × 0.7.
    double[] p = new double[38];
    boolean[] y = new boolean[38];
    for (int i = 35; i < 38; i++) {
      p[i] = 0.7;
    }
    y[0] = true;

    var curve = RiskEvaluation.calibrationCurve(p, y, 10);

    assertThat(curve).hasSize(2);
    assertThat(curve.get(1).lower()).isEqualTo(0.7);
    assertThat(curve.get(1).count()).isEqualTo(3);
    assertThat(curve.get(1).meanPredicted()).isEqualTo(0.7);
    assertThat(curve.get(0).meanPredicted()).isZero();
    var third = RiskEvaluation.calibrationCurve(new double[] {0.3, 0.3, 0.3}, new boolean[3], 10);
    assertThat(third).containsExactly(new RiskEvaluation.CalibrationBin(0.3, 0.4, 3, 0.3, 0));
    var top = RiskEvaluation.calibrationCurve(new double[] {1.0, 0.95}, new boolean[] {true, true}, 10);
    assertThat(top.get(0).lower()).isEqualTo(0.9);
    assertThat(top.get(0).count()).isEqualTo(2);
    assertThat(top.get(0).meanPredicted()).isCloseTo(0.975, within(1e-12));
  }

  @Test
  void timeSplitTrainsOnThePastAndTestsOnTheFuture() {
    List<LabelledExample> history = Fixtures.history(10, 1);
    List<LabelledExample> shuffled =
        List.of(
            history.get(7), history.get(2), history.get(9), history.get(0), history.get(4),
            history.get(1), history.get(8), history.get(3), history.get(6), history.get(5));

    var split = RiskEvaluation.timeSplit(shuffled, 0.7);

    assertThat(split.train()).containsExactlyElementsOf(history.subList(0, 7));
    assertThat(split.test()).containsExactlyElementsOf(history.subList(7, 10));
  }

  @Test
  void examplesSharingTheBoundaryTimestampAllGoToTest() {
    var t0 = Instant.EPOCH;
    var t1 = t0.plusSeconds(60);
    var t2 = t0.plusSeconds(120);
    var a = new LabelledExample(Fixtures.reference(), false, t0);
    var b = new LabelledExample(Fixtures.reference(), true, t1);
    var c = new LabelledExample(Fixtures.reference(), false, t1);
    var d = new LabelledExample(Fixtures.reference(), true, t2);

    var split = RiskEvaluation.timeSplit(List.of(d, b, c, a), 0.5);

    assertThat(split.train()).containsExactly(a);
    assertThat(split.test()).containsExactly(b, c, d);
    var allSame = RiskEvaluation.timeSplit(List.of(b, c), 0.5);
    assertThat(allSame.train()).isEmpty();
    assertThat(allSame.test()).containsExactly(b, c);
    assertThat(RiskEvaluation.timeSplit(List.of(), 0.5).test()).isEmpty();
    assertThatThrownBy(() -> RiskEvaluation.timeSplit(List.of(a), 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RiskEvaluation.timeSplit(List.of(a), 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RiskEvaluation.timeSplit(List.of(a), Double.NaN))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void labelledExamplesRequireFeaturesAndATime() {
    assertThatThrownBy(() -> new LabelledExample(null, true, Instant.EPOCH))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new LabelledExample(Fixtures.reference(), true, null))
        .isInstanceOf(NullPointerException.class);
  }
}
