package com.sprintflowinsight.forecast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;

/** Joint bootstrap paths for B6: all answers come from the same 10,000 simulated futures. */
public final class MonteCarloSimulation {
  public static final int TRIALS = BootstrapMonteCarloModel.DEFAULT_TRIALS;
  public static final int MAX_DAYS = ForecastAnswers.DEFAULT_MAX_DAYS;
  private static final double[] LEVELS = {0.50, 0.85, 0.95};

  private MonteCarloSimulation() {}

  public record Completion(double confidence, Integer days) {}

  public record Quantity(double confidence, long items) {}

  public record Bin(long from, long to, int count) {}

  public record Result(
      int trials,
      int horizonDays,
      List<Completion> completions,
      List<Quantity> quantities,
      double targetProbability,
      List<Bin> completionHistogram,
      int beyondHorizon,
      List<Bin> quantityHistogram) {
    public Result {
      completions = List.copyOf(completions);
      quantities = List.copyOf(quantities);
      completionHistogram = List.copyOf(completionHistogram);
      quantityHistogram = List.copyOf(quantityHistogram);
    }
  }

  public static Result simulate(
      ThroughputHistory history,
      int remaining,
      int target,
      int targetDays,
      RandomGenerator random) {
    return simulate(history, remaining, target, targetDays, random, TRIALS, MAX_DAYS);
  }

  static Result simulate(
      ThroughputHistory history,
      int remaining,
      int target,
      int targetDays,
      RandomGenerator random,
      int trials,
      int horizonDays) {
    if (history == null || random == null)
      throw new IllegalArgumentException("history and random are required");
    if (remaining < 0
        || target < 0
        || targetDays < 0
        || targetDays > horizonDays
        || trials < 1
        || horizonDays < 1
        || horizonDays > MAX_DAYS)
      throw new IllegalArgumentException("invalid work, target, trials or horizon");
    long[] completionDays = new long[trials], quantities = new long[trials];
    int reachedTarget = 0, beyond = 0;
    boolean canProgress = Arrays.stream(history.counts()).anyMatch(count -> count > 0);
    for (int trial = 0; trial < trials; trial++) {
      int completedAt = remaining == 0 ? 0 : horizonDays + 1;
      long delivered = 0;
      boolean targetKnown = targetDays == 0;
      if (canProgress) {
        // Fixed paths keep completion dates unchanged when only the target date changes,
        // and keep by-date answers unchanged when only the amount of remaining work changes.
        for (int day = 0; day < horizonDays; ) {
          int duration = Math.min(history.periodDays(), horizonDays - day);
          int draw = history.count(random.nextInt(history.size()));
          // Uniform accrual within a sampled period is an explicit modelling assumption.
          // A single shared path makes date confidence levels and target answers coherent.
          if (!targetKnown && targetDays <= day + duration) {
            quantities[trial] = delivered + (long) draw * (targetDays - day) / history.periodDays();
            targetKnown = true;
          }
          long inPeriod = (long) draw * duration / history.periodDays();
          long needed = remaining - delivered;
          if (completedAt > horizonDays && needed > 0 && needed <= inPeriod) {
            completedAt = day + (int) ((needed * history.periodDays() + draw - 1) / draw);
          }
          delivered += inPeriod;
          day += duration;
        }
      }
      completionDays[trial] = completedAt;
      if (completedAt > horizonDays) beyond++;
      if (quantities[trial] >= target) reachedTarget++;
    }
    Arrays.sort(completionDays);
    Arrays.sort(quantities);
    List<Completion> dates = new ArrayList<>();
    List<Quantity> amounts = new ArrayList<>();
    for (double confidence : LEVELS) {
      long day = completionDays[(int) Math.ceil(confidence * trials) - 1];
      dates.add(new Completion(confidence, day > horizonDays ? null : (int) day));
      // Largest x with at least confidence of trials completing x or more (ties included).
      int index = (int) Math.floor((1 - confidence) * trials + 1e-9);
      amounts.add(new Quantity(confidence, quantities[Math.min(index, trials - 1)]));
    }
    return new Result(
        trials,
        horizonDays,
        dates,
        amounts,
        reachedTarget / (double) trials,
        bins(Arrays.copyOf(completionDays, trials - beyond)),
        beyond,
        bins(quantities));
  }

  /** Bounded integer-width histogram; no allocation proportional to the range of work counts. */
  private static List<Bin> bins(long[] sorted) {
    if (sorted.length == 0) return List.of();
    long min = sorted[0], max = sorted[sorted.length - 1];
    long width = Math.max(1, (max - min + 20) / 20);
    int size = (int) ((max - min) / width + 1);
    int[] counts = new int[size];
    for (long value : sorted) counts[(int) ((value - min) / width)]++;
    List<Bin> result = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      long from = min + i * width;
      result.add(new Bin(from, Math.min(max, from + width - 1), counts[i]));
    }
    return result;
  }
}
