package com.sprintflowinsight.risk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * An immutable logistic risk model: {@code p = σ(b + Σ w_j z_j)} with {@code z} the standardised
 * features. Both the trained model and the cold-start heuristic have this form, so predictions and
 * explanations are produced the same way.
 */
public final class RiskModel {
  /**
   * Probabilities are clamped to {@code [EPSILON, 1 - EPSILON]}, so they stay strictly in (0, 1).
   */
  public static final double EPSILON = 1e-9;

  /** Number of reasons in a prediction's explanation. */
  public static final int TOP_REASONS = 3;

  /** Origin of a model. */
  public enum Source {
    /** Fitted by {@link LogisticRegression} on the instance's own history. */
    TRAINED,
    /** Expert weights with an intercept calibrated to the observed late rate. */
    HEURISTIC
  }

  private final Source source;
  private final Standardizer standardizer;
  private final double intercept;
  private final double[] weights;

  RiskModel(Source source, Standardizer standardizer, double intercept, double[] weights) {
    if (weights.length != RiskFeature.COUNT || standardizer.means().length != RiskFeature.COUNT) {
      throw new IllegalArgumentException("expected " + RiskFeature.COUNT + " features");
    }
    this.source = Objects.requireNonNull(source);
    this.standardizer = standardizer;
    this.intercept = intercept;
    this.weights = weights.clone();
  }

  /** Rebuild a previously fitted model from its stored parameters. */
  public static RiskModel restore(
      Source source, double intercept, double[] means, double[] scales, double[] weights) {
    return new RiskModel(source, Standardizer.of(means, scales), intercept, weights);
  }

  public RiskPrediction predict(RiskFeatures features) {
    double[] contributions = contributions(features);
    double logOdds = intercept;
    for (double c : contributions) {
      logOdds += c;
    }
    return new RiskPrediction(
        clamp(LogisticRegression.sigmoid(logOdds)), source, top(contributions));
  }

  public double probability(RiskFeatures features) {
    return predict(features).probability();
  }

  /** Coefficient × standardised value per feature, in {@link RiskFeature} order. */
  double[] contributions(RiskFeatures features) {
    double[] z = standardizer.transform(features.toVector());
    double[] c = new double[z.length];
    for (int j = 0; j < z.length; j++) {
      c[j] = weights[j] * z[j];
    }
    return c;
  }

  private static List<RiskPrediction.Contribution> top(double[] contributions) {
    List<RiskPrediction.Contribution> all = new ArrayList<>();
    for (RiskFeature feature : RiskFeature.values()) {
      double value = contributions[feature.ordinal()];
      if (value != 0) {
        all.add(new RiskPrediction.Contribution(feature, value));
      }
    }
    // Stable sort: equal magnitudes keep feature order, so explanations are deterministic.
    all.sort(Comparator.comparingDouble((RiskPrediction.Contribution c) -> -Math.abs(c.logOdds())));
    return all.subList(0, Math.min(TOP_REASONS, all.size()));
  }

  static double clamp(double p) {
    return Math.min(1 - EPSILON, Math.max(EPSILON, p));
  }

  public Source source() {
    return source;
  }

  public double intercept() {
    return intercept;
  }

  /** Coefficients on the standardised scale, in {@link RiskFeature} order. */
  public double[] weights() {
    return weights.clone();
  }

  public Standardizer standardizer() {
    return standardizer;
  }
}
