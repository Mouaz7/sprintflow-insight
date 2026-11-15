package com.sprintflowinsight.forecast;

/**
 * Rolling-origin settings. Each origin o uses periods [0, o) to forecast the total of the next
 * {@code horizonPeriods} periods; origins start at {@code minTrainingPeriods} and advance by one.
 * A model can only be selected when at least {@code minOrigins} origins fit in the history.
 */
public record BacktestConfig(
    int minTrainingPeriods, int horizonPeriods, int minOrigins, int pitBins) {
  public static final BacktestConfig DEFAULT = new BacktestConfig(8, 2, 6, 10);

  public BacktestConfig {
    if (minTrainingPeriods < 1 || horizonPeriods < 1 || minOrigins < 1 || pitBins < 1) {
      throw new IllegalArgumentException("backtest settings must be positive");
    }
  }

  /** Number of forecast origins available in a history of the given length (never negative). */
  public int origins(int historyPeriods) {
    return Math.max(0, historyPeriods - minTrainingPeriods - horizonPeriods + 1);
  }

  /** Shortest history that satisfies the minimum-data rule. */
  public int requiredPeriods() {
    return minTrainingPeriods + horizonPeriods + minOrigins - 1;
  }
}
