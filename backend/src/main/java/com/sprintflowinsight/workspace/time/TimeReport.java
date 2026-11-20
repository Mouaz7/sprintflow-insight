package com.sprintflowinsight.workspace.time;

import com.sprintflowinsight.CsvCell;
import java.util.ArrayList;
import java.util.List;

/**
 * Estimated against actual time for the tasks in a project or sprint.
 *
 * @param estimatedMinutes sum of all estimates
 * @param loggedMinutes all logged time, also on unestimated tasks
 * @param loggedOnEstimatedMinutes logged time on estimated tasks only, the fair comparison
 * @param varianceMinutes {@code loggedOnEstimatedMinutes - estimatedMinutes}
 * @param variancePercent the variance relative to the estimates; {@code null} when they sum to 0
 */
public record TimeReport(
    Long sprintId,
    List<TimeReportRow> rows,
    long estimatedMinutes,
    long loggedMinutes,
    long loggedOnEstimatedMinutes,
    long unestimatedTasks,
    long varianceMinutes,
    Integer variancePercent) {

  /** A task with its logged minutes, before the variance is computed. */
  public record Line(
      Long taskId,
      String key,
      String title,
      String status,
      String assigneeEmail,
      Integer estimateMinutes,
      long logged) {}

  /** One row per task with the variance, and the totals computed from them. */
  public static TimeReport of(Long sprintId, List<Line> lines) {
    List<TimeReportRow> rows = new ArrayList<>();
    long estimated = 0;
    long logged = 0;
    long loggedOnEstimated = 0;
    long unestimated = 0;
    for (Line line : lines) {
      CapacityMath.Variance variance = CapacityMath.variance(line.estimateMinutes(), line.logged());
      rows.add(
          new TimeReportRow(
              line.taskId(),
              line.key(),
              line.title(),
              line.status(),
              line.assigneeEmail(),
              line.estimateMinutes(),
              line.logged(),
              variance.minutes(),
              variance.percent()));
      logged += line.logged();
      if (line.estimateMinutes() == null) {
        unestimated++;
      } else {
        estimated += line.estimateMinutes();
        loggedOnEstimated += line.logged();
      }
    }
    long variance = loggedOnEstimated - estimated;
    Integer percent = estimated == 0 ? null : (int) Math.round(variance * 100.0 / estimated);
    return new TimeReport(
        sprintId,
        List.copyOf(rows),
        estimated,
        logged,
        loggedOnEstimated,
        unestimated,
        variance,
        percent);
  }

  /** CSV with a header row; text cells are quoted and protected against formula injection. */
  public String toCsv() {
    StringBuilder csv =
        new StringBuilder(
            "Key,Title,Status,Assignee,Estimate minutes,Logged minutes,Variance minutes,"
                + "Variance percent\n");
    for (TimeReportRow row : rows) {
      csv.append(CsvCell.quote(row.key()))
          .append(',')
          .append(CsvCell.quote(row.title()))
          .append(',')
          .append(CsvCell.quote(row.status()))
          .append(',')
          .append(CsvCell.quote(row.assigneeEmail()))
          .append(',')
          .append(number(row.estimateMinutes()))
          .append(',')
          .append(row.loggedMinutes())
          .append(',')
          .append(number(row.varianceMinutes()))
          .append(',')
          .append(number(row.variancePercent()))
          .append('\n');
    }
    csv.append(CsvCell.quote("Total"))
        .append(",,,,")
        .append(estimatedMinutes)
        .append(',')
        .append(loggedMinutes)
        .append(',')
        .append(varianceMinutes)
        .append(',')
        .append(number(variancePercent))
        .append('\n');
    return csv.toString();
  }

  private static String number(Number value) {
    return value == null ? "" : value.toString();
  }
}
