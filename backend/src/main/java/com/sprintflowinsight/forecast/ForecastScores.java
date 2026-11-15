package com.sprintflowinsight.forecast;

/** Proper scoring and calibration primitives for integer-valued forecasts. */
public final class ForecastScores {
  private ForecastScores() {}

  /**
   * Continuous ranked probability score CRPS(F, y) = ∫ (F(x) − 1{x ≥ y})² dx. For a distribution
   * on the integers F is a step function with unit steps, so the integral is exactly the sum over
   * integers k of (F(k) − 1{k ≥ y})². Terms outside the union of the support and y are zero. Lower
   * is better; it is zero only for a point mass at the observation and has the unit of the counts.
   */
  public static double crps(DiscreteDistribution forecast, int observed) {
    int from = Math.min(forecast.min(), observed);
    int to = Math.max(forecast.max(), observed);
    double sum = 0;
    for (int k = from; k <= to; k++) {
      double difference = forecast.cdf(k) - (k >= observed ? 1.0 : 0.0);
      sum += difference * difference;
    }
    return sum;
  }

  /**
   * Whether the observation lies in the central interval [q((1−c)/2), q((1+c)/2)] of nominal
   * coverage c. On the integers the interval holds at least c of the mass, so a calibrated model
   * covers slightly more often than nominal.
   */
  public static boolean covers(DiscreteDistribution forecast, int observed, double level) {
    if (!(level > 0 && level < 1)) throw new IllegalArgumentException("level must be in (0, 1)");
    return forecast.quantile((1 - level) / 2) <= observed
        && observed <= forecast.quantile((1 + level) / 2);
  }

  /**
   * Non-randomised conditional PIT for count data (Czado, Gneiting and Held 2009): the
   * distribution function at u of a value spread uniformly over [F(y−1), F(y)]. Averaging it over
   * forecast cases gives a PIT histogram that is uniform for a calibrated model.
   */
  public static double conditionalPit(DiscreteDistribution forecast, int observed, double u) {
    double below = forecast.cdf(observed - 1);
    double atOrBelow = forecast.cdf(observed);
    if (u >= atOrBelow) return 1.0;
    if (u <= below) return 0.0;
    return (u - below) / (atOrBelow - below);
  }
}
