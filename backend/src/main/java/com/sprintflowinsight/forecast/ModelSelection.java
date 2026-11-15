package com.sprintflowinsight.forecast;

import java.util.List;

/** Outcome of choosing a forecast model for one history. */
public sealed interface ModelSelection {
  /** The model with the lowest mean CRPS, with every candidate's report for explanation. */
  record Selected(ForecastModel model, BacktestReport best, List<BacktestReport> reports)
      implements ModelSelection {
    public Selected {
      reports = List.copyOf(reports);
    }
  }

  /** Too little history to backtest; callers should fall back to a default model or no forecast. */
  record InsufficientHistory(int availablePeriods, int requiredPeriods)
      implements ModelSelection {}
}
