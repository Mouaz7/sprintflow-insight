package com.sprintflowinsight.reporting;

import com.sprintflowinsight.forecast.BacktestReport;
import com.sprintflowinsight.forecast.ThroughputUnit;
import java.util.List;

/** Rolling-origin comparison and the selected model's forecast at the evaluated horizon. */
public record ForecastBacktestResponse(
    ForecastBasis basis,
    ThroughputUnit unit,
    int periodDays,
    int availablePeriods,
    int requiredPeriods,
    int trainingPeriods,
    int horizonPeriods,
    String seed,
    Status status,
    String selectedModel,
    Prediction prediction,
    List<BacktestReport> candidates) {

  public ForecastBacktestResponse {
    candidates = List.copyOf(candidates);
  }

  public enum Status {
    SELECTED,
    INSUFFICIENT_HISTORY,
    NO_COMPLETIONS
  }

  public record Prediction(int median, List<Interval> intervals) {
    public Prediction {
      intervals = List.copyOf(intervals);
    }
  }

  public record Interval(double confidence, int lower, int upper) {}
}
