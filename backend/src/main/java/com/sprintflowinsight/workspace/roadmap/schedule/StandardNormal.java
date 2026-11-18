package com.sprintflowinsight.workspace.roadmap.schedule;

/** Standard normal probability, using the polynomial approximation in A&S 26.2.17. */
public final class StandardNormal {
  private StandardNormal() {}

  /** Cumulative probability; the approximation has absolute error below 7.5e-8. */
  public static double cdf(double z) {
    if (Double.isNaN(z)) throw new IllegalArgumentException("z must be a number");
    if (z == Double.POSITIVE_INFINITY) return 1;
    if (z == Double.NEGATIVE_INFINITY) return 0;
    if (z == 0) return 0.5;
    double x = Math.abs(z);
    double t = 1 / (1 + 0.2316419 * x);
    double polynomial =
        t
            * (0.319381530
                + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))));
    double tail = Math.exp(-x * x / 2) / Math.sqrt(2 * Math.PI) * polynomial;
    return z > 0 ? 1 - tail : tail;
  }
}
