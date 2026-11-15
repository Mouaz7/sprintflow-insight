package com.sprintflowinsight.forecast;

import java.util.List;
import java.util.random.RandomGenerator;

/** A throughput model that turns history into a predictive distribution for a future horizon. */
public interface ForecastModel {
  /** Stable identifier, used in backtest reports and model selection. */
  String name();

  /**
   * Predictive distribution of the total completed in the next {@code periods} periods after the
   * history ends. Fractional periods are allowed; zero periods yield a point mass at zero.
   *
   * @throws IllegalArgumentException if the history is too short for this model or periods is
   *     negative or not finite
   */
  DiscreteDistribution forecast(ThroughputHistory history, double periods);

  /** The three candidate models with default settings, in tie-breaking order. */
  static List<ForecastModel> defaults(RandomGenerator random) {
    return List.of(
        new GammaPoissonModel(), new EwmaPoissonModel(), new BootstrapMonteCarloModel(random));
  }

  static void requireHorizon(double periods) {
    if (!(periods >= 0) || Double.isInfinite(periods)) {
      throw new IllegalArgumentException("periods must be finite and non-negative");
    }
  }
}
