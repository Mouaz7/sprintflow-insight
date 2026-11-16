package com.sprintflowinsight.risk;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Risk model for instances with too little labelled history (cold start).
 *
 * <p>The direction and relative size of each weight are expert priors on fixed reference scales;
 * they are not learned. Only the intercept is calibrated: it is chosen so that the mean predicted
 * probability over the available examples equals the observed late rate, shrunk towards a prior
 * rate of {@value #PRIOR_LATE_RATE} with {@value #PRIOR_STRENGTH} pseudo-observations (a Beta(1,
 * 3) prior). With no examples the heuristic predicts the prior rate for a reference task.
 *
 * <p>The intercept is found by bisection on a bracket that always contains it: since {@code
 * σ(b + min s) ≤ mean σ(b + s) ≤ σ(b + max s)}, the solution lies in {@code [logit(t) − max s,
 * logit(t) − min s]} for scores {@code s} and target {@code t}. There is no fixed bound that it
 * could silently pin to. If the scores are not finite or the target is still missed, a warning is
 * logged and the uncalibrated reference intercept {@code logit(t)} is used.
 */
public final class ColdStartHeuristic {
  /** Prior share of tasks not done at sprint end. */
  public static final double PRIOR_LATE_RATE = 0.25;

  /** Weight of the prior, in examples. */
  public static final double PRIOR_STRENGTH = 4;

  // Reference task: in a column as long as is typical, never blocked, 3 points, 5 days left.
  private static final double[] REFERENCE = {1, 0, 0, 0, 3, 0, 5, 3};
  private static final double[] SCALE = {1, 1, 1, 3, 3, 1, 3, 3};
  // Log-odds change per reference unit; positive means more risk.
  private static final double[] WEIGHTS = {1.0, 0.6, 0.3, 0.1, 0.4, 0.4, -0.8, 0.3};

  private static final Logger LOG = LoggerFactory.getLogger(ColdStartHeuristic.class);
  private static final int BISECTION_STEPS = 200;
  /** Largest accepted gap between the calibrated mean probability and the target. */
  static final double CALIBRATION_TOLERANCE = 1e-9;

  private ColdStartHeuristic() {}

  /** The heuristic calibrated to {@code examples} (which may be empty). */
  public static RiskModel calibrated(List<LabelledExample> examples) {
    Standardizer reference = Standardizer.of(REFERENCE, SCALE);
    RiskModel uncalibrated = new RiskModel(RiskModel.Source.HEURISTIC, reference, 0, WEIGHTS);
    long late = examples.stream().filter(LabelledExample::late).count();
    double target = (late + PRIOR_STRENGTH * PRIOR_LATE_RATE) / (examples.size() + PRIOR_STRENGTH);
    double[] scores = new double[examples.size()];
    for (int i = 0; i < scores.length; i++) {
      for (double c : uncalibrated.contributions(examples.get(i).features())) {
        scores[i] += c;
      }
    }
    double intercept = examples.isEmpty() ? logit(target) : solveIntercept(scores, target);
    return new RiskModel(RiskModel.Source.HEURISTIC, reference, intercept, WEIGHTS);
  }

  /** The intercept whose mean σ(intercept + score) equals {@code target}; monotone, so bisection. */
  static double solveIntercept(double[] scores, double target) {
    double fallback = logit(target);
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    for (double s : scores) {
      min = Math.min(min, s);
      max = Math.max(max, s);
    }
    double low = fallback - max;
    double high = fallback - min;
    if (!Double.isFinite(low) || !Double.isFinite(high)) {
      LOG.warn("Cold-start calibration skipped: non-finite risk scores; using the prior intercept");
      return fallback;
    }
    for (int step = 0; step < BISECTION_STEPS; step++) {
      double mid = low + (high - low) / 2;
      if (meanProbability(scores, mid) < target) {
        low = mid;
      } else {
        high = mid;
      }
    }
    double intercept = low + (high - low) / 2;
    double reached = meanProbability(scores, intercept);
    if (!(Math.abs(reached - target) <= CALIBRATION_TOLERANCE)) {
      LOG.warn(
          "Cold-start calibration reached a mean of {} instead of {}; using the prior intercept",
          reached,
          target);
      return fallback;
    }
    return intercept;
  }

  private static double meanProbability(double[] scores, double intercept) {
    double sum = 0;
    for (double s : scores) {
      sum += LogisticRegression.sigmoid(intercept + s);
    }
    return sum / scores.length;
  }

  static double logit(double p) {
    return Math.log(p / (1 - p));
  }
}
