package com.sprintflowinsight.forecast;

/**
 * Poisson throughput whose rate is an exponentially weighted mean of past periods: period i of n
 * has weight d^(n-1-i) with d = 0.5^(1/halfLife), so the latest period weighs most and a period
 * {@code halfLife} periods older weighs half as much. The plug-in rate ignores its own
 * uncertainty, so intervals are narrower than the Gamma-Poisson model's.
 */
public final class EwmaPoissonModel implements ForecastModel {
  public static final String NAME = "ewma-poisson";
  public static final double DEFAULT_HALF_LIFE = 4.0;

  private final double decay;

  public EwmaPoissonModel() {
    this(DEFAULT_HALF_LIFE);
  }

  public EwmaPoissonModel(double halfLifePeriods) {
    if (!(halfLifePeriods > 0) || Double.isInfinite(halfLifePeriods)) {
      throw new IllegalArgumentException("half-life must be finite and positive");
    }
    this.decay = Math.pow(0.5, 1.0 / halfLifePeriods);
  }

  @Override
  public String name() {
    return NAME;
  }

  /** The exponentially weighted mean count per period. */
  public double rate(ThroughputHistory history) {
    if (history.size() == 0) throw new IllegalArgumentException("history is empty");
    double weighted = 0;
    double weights = 0;
    double weight = 1;
    for (int i = history.size() - 1; i >= 0; i--) {
      weighted += weight * history.count(i);
      weights += weight;
      weight *= decay;
    }
    return weighted / weights;
  }

  @Override
  public DiscreteDistribution forecast(ThroughputHistory history, double periods) {
    ForecastModel.requireHorizon(periods);
    return DiscreteDistribution.poisson(rate(history) * periods);
  }
}
