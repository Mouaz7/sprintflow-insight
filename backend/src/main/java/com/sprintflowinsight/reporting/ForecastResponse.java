package com.sprintflowinsight.reporting;

import com.sprintflowinsight.forecast.MonteCarloSimulation.Bin;
import com.sprintflowinsight.forecast.ThroughputUnit;
import java.time.LocalDate;
import java.util.List;

/** B6 bootstrap answers from the same 10,000 paths; model comparison is deferred to C2. */
public record ForecastResponse(
    ForecastBasis basis,
    ThroughputUnit unit,
    int periodDays,
    LocalDate start,
    String seed,
    int simulations,
    int minimumPeriods,
    List<HistoryPeriod> history,
    List<Warning> warnings,
    WorkScope scope,
    int remaining,
    List<CompletionDate> completionDates,
    List<Bin> completionHistogram,
    int beyondHorizon,
    int horizonDays,
    ItemsByDate itemsByDate,
    TargetProbability target) {
  public ForecastResponse {
    history = List.copyOf(history);
    warnings = List.copyOf(warnings);
    completionDates = List.copyOf(completionDates);
    completionHistogram = List.copyOf(completionHistogram);
  }

  public record HistoryPeriod(LocalDate start, LocalDate end, int count, String label) {}

  public enum Warning {
    NO_HISTORY,
    NO_COMPLETIONS,
    INSUFFICIENT_HISTORY,
    MISSING_TASK_HISTORY,
    UNPLANNED_STORIES,
    ZERO_POINT_TASKS,
    EPIC_USES_PROJECT_HISTORY,
    PAST_SPRINT_DEADLINE,
    TARGET_BEYOND_HORIZON
  }

  /** The target covers recorded work. Project-wide capacity is used even for one epic. */
  public record WorkScope(
      Long epicId,
      String epicTitle,
      int openTasks,
      long openStoryPoints,
      int unplannedStories,
      int zeroPointTasks,
      int tasksWithoutHistory) {}

  /** Null date means the sample cannot support this confidence within the five-year horizon. */
  public record CompletionDate(double confidence, LocalDate date) {}

  public enum TargetSource {
    REQUEST,
    ACTIVE_SPRINT,
    DEFAULT
  }

  public record ItemsAtConfidence(double confidence, long items) {}

  public record ItemsByDate(
      LocalDate date, TargetSource source, List<ItemsAtConfidence> items, List<Bin> histogram) {
    public ItemsByDate {
      items = List.copyOf(items);
      histogram = List.copyOf(histogram);
    }
  }

  public record TargetProbability(
      int items, LocalDate date, TargetSource source, Double probability) {}
}
