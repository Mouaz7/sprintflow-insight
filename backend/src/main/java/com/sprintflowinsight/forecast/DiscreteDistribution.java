package com.sprintflowinsight.forecast;

import java.util.Arrays;

/**
 * A probability distribution on a contiguous range of non-negative integers, stored as its
 * probability mass function. Exact families (Poisson, negative binomial) are evaluated by the
 * pmf's ratio recurrence from the mode and truncated where the remaining mass is below about
 * 1e-15; empirical distributions come from samples.
 */
public final class DiscreteDistribution {
  /** Relative pmf value (to the mode) below which tails are truncated. */
  static final double TAIL = 1e-17;

  /** Slack for floating-point comparisons against cumulative probabilities. */
  static final double EPSILON = 1e-12;

  static final int MAX_SUPPORT = 5_000_000;

  private final int offset;
  private final double[] pmf;
  private final double[] cdf;

  private DiscreteDistribution(int offset, double[] weights) {
    double total = 0;
    for (double weight : weights) total += weight;
    this.offset = offset;
    this.pmf = new double[weights.length];
    this.cdf = new double[weights.length];
    double running = 0;
    for (int i = 0; i < weights.length; i++) {
      pmf[i] = weights[i] / total;
      running += pmf[i];
      cdf[i] = Math.min(running, 1.0);
    }
    cdf[weights.length - 1] = 1.0;
  }

  /** All mass on one value. */
  public static DiscreteDistribution pointMass(int value) {
    return ofWeights(value, new double[] {1.0});
  }

  /**
   * Normalises non-negative weights for the values {@code offset, offset + 1, ...}.
   *
   * @throws IllegalArgumentException for an empty, negative, non-finite or all-zero weight vector
   */
  public static DiscreteDistribution ofWeights(int offset, double[] weights) {
    if (offset < 0) throw new IllegalArgumentException("support must be non-negative");
    if (weights.length == 0) throw new IllegalArgumentException("weights must not be empty");
    double total = 0;
    for (double weight : weights) {
      if (!(weight >= 0) || Double.isInfinite(weight)) {
        throw new IllegalArgumentException("weights must be finite and non-negative");
      }
      total += weight;
    }
    if (total <= 0) throw new IllegalArgumentException("weights must not all be zero");
    return new DiscreteDistribution(offset, weights.clone());
  }

  /** The empirical distribution of the given non-negative integer samples. */
  public static DiscreteDistribution ofSamples(int[] samples) {
    if (samples.length == 0) throw new IllegalArgumentException("samples must not be empty");
    int min = Arrays.stream(samples).min().orElseThrow();
    int max = Arrays.stream(samples).max().orElseThrow();
    double[] weights = new double[max - min + 1];
    for (int sample : samples) weights[sample - min]++;
    return ofWeights(min, weights);
  }

  /** Poisson distribution with the given mean (a point mass at zero for mean zero). */
  public static DiscreteDistribution poisson(double mean) {
    if (!(mean >= 0) || Double.isInfinite(mean)) {
      throw new IllegalArgumentException("mean must be finite and non-negative");
    }
    if (mean == 0) return pointMass(0);
    return fromMode((int) Math.floor(mean), k -> mean / (k + 1), k -> k / mean);
  }

  /**
   * Negative binomial with {@code shape} r and success probability {@code p = rate / (rate +
   * periods)}: the posterior predictive of a Poisson count over {@code periods} periods whose
   * per-period rate has a Gamma(shape, rate) distribution. P(k) = Γ(k+r) / (k! Γ(r)) p^r (1-p)^k.
   */
  public static DiscreteDistribution gammaPoissonPredictive(
      double shape, double rate, double periods) {
    if (!(shape > 0) || !(rate > 0) || Double.isInfinite(shape) || Double.isInfinite(rate)) {
      throw new IllegalArgumentException("shape and rate must be finite and positive");
    }
    ForecastModel.requireHorizon(periods);
    if (periods == 0) return pointMass(0);
    double p = rate / (rate + periods);
    double q = periods / (rate + periods);
    int mode = shape > 1 ? (int) Math.floor((shape - 1) * periods / rate) : 0;
    return fromMode(mode, k -> (k + shape) / (k + 1) * q, k -> k / ((k - 1 + shape) * q));
  }

  @FunctionalInterface
  private interface Ratio {
    double at(int k);
  }

  /**
   * Builds a unimodal pmf from its mode by the ratios {@code up(k) = P(k+1)/P(k)} and {@code
   * down(k) = P(k-1)/P(k)}, then normalises. Starting at the mode keeps every term at most one,
   * so neither direction can overflow.
   */
  private static DiscreteDistribution fromMode(int mode, Ratio up, Ratio down) {
    double[] upper = new double[16];
    upper[0] = 1.0;
    int size = 1;
    for (int k = mode; ; k++) {
      double next = upper[size - 1] * up.at(k);
      if (next < TAIL) break;
      if (size == upper.length) upper = Arrays.copyOf(upper, grow(size));
      upper[size++] = next;
    }
    double[] lower = new double[16];
    int below = 0;
    double term = 1.0;
    for (int k = mode; k > 0; k--) {
      term *= down.at(k);
      if (term < TAIL) break;
      if (below == lower.length) lower = Arrays.copyOf(lower, grow(below));
      lower[below++] = term;
    }
    double[] weights = new double[below + size];
    for (int i = 0; i < below; i++) weights[below - 1 - i] = lower[i];
    System.arraycopy(upper, 0, weights, below, size);
    return new DiscreteDistribution(mode - below, weights);
  }

  private static int grow(int size) {
    if (size >= MAX_SUPPORT) throw new IllegalArgumentException("distribution is too wide");
    return size * 2;
  }

  /** Smallest value with positive stored probability. */
  public int min() {
    return offset;
  }

  /** Largest stored value; mass above it is below the truncation tolerance. */
  public int max() {
    return offset + pmf.length - 1;
  }

  public double pmf(int value) {
    int index = value - offset;
    return index < 0 || index >= pmf.length ? 0.0 : pmf[index];
  }

  /** P(X ≤ value). */
  public double cdf(int value) {
    int index = value - offset;
    if (index < 0) return 0.0;
    return index >= cdf.length ? 1.0 : cdf[index];
  }

  /** P(X ≥ value). */
  public double probabilityAtLeast(int value) {
    return 1.0 - cdf(value - 1);
  }

  /**
   * The smallest value x with P(X ≤ x) ≥ probability (the left-continuous inverse of the cdf).
   *
   * @throws IllegalArgumentException unless 0 ≤ probability ≤ 1
   */
  public int quantile(double probability) {
    if (!(probability >= 0 && probability <= 1)) {
      throw new IllegalArgumentException("probability must be within [0, 1]");
    }
    double target = probability - EPSILON;
    int low = 0;
    int high = cdf.length - 1;
    while (low < high) {
      int middle = (low + high) >>> 1;
      if (cdf[middle] >= target) high = middle;
      else low = middle + 1;
    }
    return offset + low;
  }

  public int median() {
    return quantile(0.5);
  }

  public double mean() {
    double sum = 0;
    for (int i = 0; i < pmf.length; i++) sum += pmf[i] * (offset + i);
    return sum;
  }

  public double variance() {
    double mean = mean();
    double sum = 0;
    for (int i = 0; i < pmf.length; i++) {
      double deviation = offset + i - mean;
      sum += pmf[i] * deviation * deviation;
    }
    return sum;
  }

  @Override
  public String toString() {
    return "DiscreteDistribution[" + min() + ".." + max() + ", mean=" + mean() + "]";
  }
}
