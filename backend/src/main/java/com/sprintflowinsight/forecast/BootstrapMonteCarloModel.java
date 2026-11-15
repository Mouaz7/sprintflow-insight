package com.sprintflowinsight.forecast;

import java.util.random.RandomGenerator;

/**
 * Resamples observed periods with replacement (the B6 Monte Carlo forecast). Each trial sums one
 * randomly drawn historical period per future period; a fractional last period keeps each item of
 * one more draw with probability equal to the fraction (binomial thinning).
 */
public final class BootstrapMonteCarloModel implements ForecastModel {
  public static final String NAME = "bootstrap-monte-carlo";
  public static final int DEFAULT_TRIALS = 10_000;

  private final RandomGenerator random;
  private final int trials;

  public BootstrapMonteCarloModel(RandomGenerator random) {
    this(random, DEFAULT_TRIALS);
  }

  public BootstrapMonteCarloModel(RandomGenerator random, int trials) {
    if (random == null) throw new IllegalArgumentException("random is required");
    if (trials <= 0) throw new IllegalArgumentException("trials must be positive");
    this.random = random;
    this.trials = trials;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public DiscreteDistribution forecast(ThroughputHistory history, double periods) {
    ForecastModel.requireHorizon(periods);
    if (history.size() == 0) throw new IllegalArgumentException("history is empty");
    int whole = (int) Math.floor(periods);
    double fraction = periods - whole;
    int[] samples = new int[trials];
    for (int trial = 0; trial < trials; trial++) {
      int sum = 0;
      for (int period = 0; period < whole; period++) sum += draw(history);
      if (fraction > 0) {
        int partial = draw(history);
        for (int item = 0; item < partial; item++) {
          if (random.nextDouble() < fraction) sum++;
        }
      }
      samples[trial] = sum;
    }
    return DiscreteDistribution.ofSamples(samples);
  }

  private int draw(ThroughputHistory history) {
    return history.count(random.nextInt(history.size()));
  }
}
