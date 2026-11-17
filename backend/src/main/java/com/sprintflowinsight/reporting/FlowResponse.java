package com.sprintflowinsight.reporting;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Flow analytics for one project over a window of whole UTC days. Durations are hours unless the
 * name says otherwise; values are rounded to two decimals.
 */
public record FlowResponse(
    int days,
    LocalDate start,
    LocalDate end,
    Instant generatedAt,
    List<CumulativeFlowDay> cumulativeFlow,
    Distribution cycleTimeHours,
    Distribution leadTimeHours,
    int completedTasks,
    double throughputPerDay,
    List<AgingTask> agingWip,
    List<Column> columns,
    ControlChart controlChart,
    LittlesLawCheck littlesLaw,
    int tasksWithoutHistory) {

  /** Tasks in each column at the end of the day (at generation time for today). */
  public record CumulativeFlowDay(LocalDate date, int toDo, int inProgress, int review, int done) {}

  /** Percentiles use linear interpolation (Hyndman–Fan type 7); all null when count is 0. */
  public record Distribution(int count, Double mean, Double p50, Double p85, Double p95) {}

  /**
   * @param band {@code BELOW_P50}, {@code P50_TO_P85}, {@code P85_TO_P95}, {@code ABOVE_P95} or
   *     {@code NO_REFERENCE}
   */
  public record AgingTask(
      long taskId,
      String key,
      String title,
      String status,
      double ageHours,
      double statusAgeHours,
      String band) {}

  /**
   * @param limit configured WIP limit, null when none
   * @param bottleneck {@code OVER_LIMIT}, {@code HIGHEST_AVERAGE_WIP} or null
   */
  public record Column(
      String status,
      int currentWip,
      double averageWip,
      Integer limit,
      boolean overLimit,
      String bottleneck) {}

  /**
   * XmR chart of cycle time per completed task in completion order. {@code lowerLimit} is clamped
   * at zero for display; limits are null when the chart is unavailable.
   */
  public record ControlChart(
      boolean available,
      int minimumPoints,
      Double mean,
      Double upperLimit,
      Double lowerLimit,
      Double movingRangeMean,
      Double upperRangeLimit,
      int signalCount,
      List<ControlPoint> points) {}

  public record ControlPoint(
      long taskId,
      String key,
      Instant completedAt,
      double cycleTimeHours,
      Double movingRange,
      List<String> signals) {}

  /**
   * @param status {@code CONSISTENT}, {@code INCONSISTENT} or {@code INSUFFICIENT_DATA}
   * @param ratio measured / expected WIP, null when expected is 0
   */
  public record LittlesLawCheck(
      String status,
      double averageWip,
      double throughputPerDay,
      double averageCycleTimeDays,
      double expectedWip,
      Double ratio,
      double tolerance) {}
}
