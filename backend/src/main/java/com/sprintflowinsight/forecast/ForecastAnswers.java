package com.sprintflowinsight.forecast;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Planning questions answered from a model's predictive distribution. Calendar days are converted
 * to fractional periods of the history; completions are assumed to accrue evenly within a period.
 */
public final class ForecastAnswers {
  /** The confidence levels reported to users. */
  public static final List<Double> CONFIDENCE_LEVELS = List.of(0.50, 0.85, 0.95);

  /** Completion dates further than this are reported as not forecastable. */
  public static final int DEFAULT_MAX_DAYS = 5 * 365;

  private final ForecastModel model;
  private final ThroughputHistory history;
  private final int maxDays;

  public ForecastAnswers(ForecastModel model, ThroughputHistory history) {
    this(model, history, DEFAULT_MAX_DAYS);
  }

  public ForecastAnswers(ForecastModel model, ThroughputHistory history, int maxDays) {
    if (model == null || history == null) {
      throw new IllegalArgumentException("model and history are required");
    }
    if (maxDays <= 0) throw new IllegalArgumentException("maxDays must be positive");
    this.model = model;
    this.history = history;
    this.maxDays = maxDays;
  }

  /**
   * (c) Probability that at least {@code target} more items are done within {@code days} days.
   */
  public double probabilityToReach(int target, long days) {
    if (days < 0) throw new IllegalArgumentException("days must not be negative");
    if (target <= 0) return 1.0;
    return model.forecast(history, history.periodsFor(days)).probabilityAtLeast(target);
  }

  /**
   * (a) The earliest date by which {@code items} more items are done with at least the given
   * confidence, or empty when that takes longer than the configured maximum. The search assumes
   * P(done within d days) grows with d, which holds exactly for the Poisson-type models; for the
   * bootstrap model Monte Carlo noise can shift the answer by a day near the threshold.
   */
  public Optional<LocalDate> completionDate(int items, LocalDate start, double confidence) {
    requireConfidence(confidence);
    if (items <= 0) return Optional.of(start);
    if (probabilityToReach(items, maxDays) < confidence) return Optional.empty();
    long low = 0; // known to fall short (or the start itself)
    long high = maxDays; // known to suffice
    long probe = 1;
    while (probe < high && probabilityToReach(items, probe) < confidence) {
      low = probe;
      probe *= 2;
    }
    if (probe < high) high = probe;
    while (high - low > 1) {
      long middle = (low + high) >>> 1;
      if (probabilityToReach(items, middle) >= confidence) high = middle;
      else low = middle;
    }
    return Optional.of(start.plusDays(high));
  }

  /** (a) Completion dates at 50, 85 and 95 % confidence; empty when beyond the maximum. */
  public Map<Double, Optional<LocalDate>> completionDates(int items, LocalDate start) {
    Map<Double, Optional<LocalDate>> dates = new LinkedHashMap<>();
    for (double level : CONFIDENCE_LEVELS) dates.put(level, completionDate(items, start, level));
    return dates;
  }

  /**
   * (b) For each confidence level c, the largest number x of items such that P(at least x done
   * between start and end) ≥ c.
   */
  public Map<Double, Integer> itemsByDate(LocalDate start, LocalDate end) {
    long days = ChronoUnit.DAYS.between(start, end);
    if (days < 0) throw new IllegalArgumentException("end must not be before start");
    DiscreteDistribution distribution = model.forecast(history, history.periodsFor(days));
    Map<Double, Integer> items = new LinkedHashMap<>();
    for (double level : CONFIDENCE_LEVELS) items.put(level, itemsAtConfidence(distribution, level));
    return items;
  }

  /** The largest x with P(X ≥ x) ≥ confidence. */
  static int itemsAtConfidence(DiscreteDistribution distribution, double confidence) {
    requireConfidence(confidence);
    int x = distribution.min();
    while (x < distribution.max()
        && distribution.probabilityAtLeast(x + 1) >= confidence - DiscreteDistribution.EPSILON) {
      x++;
    }
    return x;
  }

  private static void requireConfidence(double confidence) {
    if (!(confidence > 0 && confidence <= 1)) {
      throw new IllegalArgumentException("confidence must be within (0, 1]");
    }
  }
}
