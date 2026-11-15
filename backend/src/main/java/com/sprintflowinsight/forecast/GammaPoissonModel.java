package com.sprintflowinsight.forecast;

/**
 * Bayesian Poisson throughput with a conjugate Gamma prior on the per-period rate λ. After n
 * periods with total s the posterior is Gamma(α₀ + s, β₀ + n) (shape, rate), and the posterior
 * predictive total over t periods is negative binomial with r = α₀ + s and p = (β₀ + n) / (β₀ + n
 * + t), evaluated exactly rather than sampled.
 */
public final class GammaPoissonModel implements ForecastModel {
  public static final String NAME = "gamma-poisson";

  /** Weak default prior: mean rate 1 per period, worth half an observed period. */
  public static final double DEFAULT_PRIOR_SHAPE = 0.5;

  public static final double DEFAULT_PRIOR_RATE = 0.5;

  private final double priorShape;
  private final double priorRate;

  public GammaPoissonModel() {
    this(DEFAULT_PRIOR_SHAPE, DEFAULT_PRIOR_RATE);
  }

  public GammaPoissonModel(double priorShape, double priorRate) {
    if (!(priorShape > 0) || !(priorRate > 0)) {
      throw new IllegalArgumentException("prior shape and rate must be positive");
    }
    if (Double.isInfinite(priorShape) || Double.isInfinite(priorRate)) {
      throw new IllegalArgumentException("prior shape and rate must be finite");
    }
    this.priorShape = priorShape;
    this.priorRate = priorRate;
  }

  @Override
  public String name() {
    return NAME;
  }

  /** Posterior shape α₀ + Σx. */
  public double posteriorShape(ThroughputHistory history) {
    return priorShape + history.total(0, history.size());
  }

  /** Posterior rate β₀ + n. */
  public double posteriorRate(ThroughputHistory history) {
    return priorRate + history.size();
  }

  @Override
  public DiscreteDistribution forecast(ThroughputHistory history, double periods) {
    return DiscreteDistribution.gammaPoissonPredictive(
        posteriorShape(history), posteriorRate(history), periods);
  }
}
