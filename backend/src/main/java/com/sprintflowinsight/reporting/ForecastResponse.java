package com.sprintflowinsight.reporting;

import com.sprintflowinsight.forecast.ThroughputUnit;
import java.time.LocalDate;
import java.util.List;

/**
 * A probabilistic delivery forecast for one project. Confidence levels are fractions (0.5 = 50 %).
 * The same data, parameters and day always yield the same response: Monte Carlo draws are seeded
 * from the history ({@code seed}).
 *
 * @param start the day the forecast starts from (today, UTC)
 * @param history the periods the forecast learned from, oldest first
 * @param remaining remaining work in {@code unit}
 * @param completionDates when the remaining work is done, per confidence level; empty without
 *     usable history
 * @param itemsByDate how much is done by the target date; {@code null} without a target date
 * @param target probability of reaching the target; {@code null} without a target date
 * @param fan predictive quantiles of cumulative completed work for the next periods
 */
public record ForecastResponse(
    ForecastBasis basis,
    ThroughputUnit unit,
    int periodDays,
    LocalDate start,
    long seed,
    List<HistoryPeriod> history,
    ModelChoice model,
    int remaining,
    List<CompletionDate> completionDates,
    ItemsByDate itemsByDate,
    TargetProbability target,
    List<FanPoint> fan) {
  public ForecastResponse {
    history = List.copyOf(history);
    completionDates = List.copyOf(completionDates);
    fan = List.copyOf(fan);
  }

  /** One observed period; {@code label} is the sprint name for velocity, otherwise null. */
  public record HistoryPeriod(LocalDate start, LocalDate end, int count, String label) {}

  /** How the model was chosen. */
  public enum SelectionStatus {
    /** Backtesting ran; the model with the lowest mean CRPS was chosen. */
    SELECTED,
    /** Too little history to backtest; the Gamma-Poisson model is used as a cautious default. */
    INSUFFICIENT_HISTORY,
    /** Nothing has been completed yet, so no forecast is made. */
    NO_HISTORY
  }

  /**
   * The model behind the answers and the evidence for choosing it.
   *
   * @param name the model used, or {@code null} for {@link SelectionStatus#NO_HISTORY}
   * @param backtest every candidate's rolling-origin scores, best first; empty unless selected
   */
  public record ModelChoice(
      SelectionStatus status,
      String name,
      int availablePeriods,
      int requiredPeriods,
      List<ModelScore> backtest) {
    public ModelChoice {
      backtest = List.copyOf(backtest);
    }
  }

  /** Out-of-sample accuracy of one candidate model (CRPS and MAE in the history unit). */
  public record ModelScore(
      String model,
      boolean selected,
      int origins,
      double meanCrps,
      double medianAbsoluteError,
      double coverage50,
      double coverage85,
      double coverage95) {}

  /** {@code date} is null when the work would take longer than the five-year horizon. */
  public record CompletionDate(double confidence, LocalDate date) {}

  /** Where a target came from: the request or the active sprint. */
  public enum TargetSource {
    REQUEST,
    ACTIVE_SPRINT
  }

  public record ItemsByDate(LocalDate date, TargetSource source, List<ItemsAtConfidence> items) {
    public ItemsByDate {
      items = List.copyOf(items);
    }
  }

  /** At least {@code items} are done with probability {@code confidence}. */
  public record ItemsAtConfidence(double confidence, int items) {}

  public record TargetProbability(
      int items, LocalDate date, TargetSource source, double probability) {}

  /** Quantiles of the cumulative work done between {@code start} and {@code date}. */
  public record FanPoint(LocalDate date, int p05, int p25, int p50, int p75, int p95) {}
}
