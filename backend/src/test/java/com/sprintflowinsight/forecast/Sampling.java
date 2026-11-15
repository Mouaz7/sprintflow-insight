package com.sprintflowinsight.forecast;

import java.util.random.RandomGenerator;

/** Reference samplers used to check exact distributions against simulation. */
final class Sampling {
  private Sampling() {}

  /** Poisson variate by inversion (fine for the moderate means used in tests). */
  static int poisson(RandomGenerator random, double mean) {
    double u = random.nextDouble();
    double term = Math.exp(-mean);
    double cumulative = term;
    int k = 0;
    while (u > cumulative && k < 10_000) {
      k++;
      term *= mean / k;
      cumulative += term;
    }
    return k;
  }

  /** Gamma(shape, rate) variate by Marsaglia and Tsang, with the boost for shape below one. */
  static double gamma(RandomGenerator random, double shape, double rate) {
    if (shape < 1) {
      return gamma(random, shape + 1, rate) * Math.pow(random.nextDouble(), 1 / shape);
    }
    double d = shape - 1.0 / 3;
    double c = 1 / Math.sqrt(9 * d);
    while (true) {
      double x = random.nextGaussian();
      double v = 1 + c * x;
      if (v <= 0) continue;
      v = v * v * v;
      double u = random.nextDouble();
      if (Math.log(u) < 0.5 * x * x + d - d * v + d * Math.log(v)) return d * v / rate;
    }
  }

  /** A synthetic Poisson throughput history. */
  static ThroughputHistory poissonHistory(RandomGenerator random, double mean, int periods) {
    int[] counts = new int[periods];
    for (int i = 0; i < periods; i++) counts[i] = poisson(random, mean);
    return ThroughputHistory.weekly(counts);
  }
}
