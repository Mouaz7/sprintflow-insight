package com.sprintflowinsight.flow;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Computes every flow measure for one project over a window of whole UTC days. All inputs are
 * already loaded; the calculation is pure and deterministic.
 */
public final class FlowAnalysis {
  private static final double MILLIS_PER_DAY = 86_400_000d;

  private FlowAnalysis() {}

  /** A task and its timeline. */
  public record Item(long taskId, TaskTimeline timeline) {}

  /**
   * @param count values behind the percentiles
   * @param mean arithmetic mean, {@code null} when {@code count == 0}
   */
  public record Distribution(int count, Double mean, Double p50, Double p85, Double p95) {
    static Distribution of(List<Double> values) {
      if (values.isEmpty()) return new Distribution(0, null, null, null, null);
      double[] sorted = Percentiles.sorted(values);
      double sum = 0;
      for (double value : sorted) sum += value;
      return new Distribution(
          sorted.length,
          sum / sorted.length,
          Percentiles.ofSorted(sorted, 0.50),
          Percentiles.ofSorted(sorted, 0.85),
          Percentiles.ofSorted(sorted, 0.95));
    }
  }

  /** Tasks in each column at the end of {@code date} (or at "now" for today). */
  public record DailyCounts(LocalDate date, Map<String, Integer> counts) {}

  /** A completion inside the window, in completion order. */
  public record CompletedItem(long taskId, Instant doneAt, Double cycleTimeHours, Double leadTimeHours) {}

  /** How an active task's age compares with the window's cycle-time percentiles. */
  public enum AgeBand {
    BELOW_P50,
    P50_TO_P85,
    P85_TO_P95,
    ABOVE_P95,
    NO_REFERENCE
  }

  /** A task currently in an active column. */
  public record AgingItem(
      long taskId, String status, double ageHours, double statusAgeHours, AgeBand band) {}

  /** Why a column is marked as the bottleneck. */
  public enum BottleneckReason {
    OVER_LIMIT,
    HIGHEST_AVERAGE_WIP
  }

  /**
   * @param limit the configured WIP limit, {@code null} when none
   * @param averageWip time-averaged number of tasks in the column over the window
   */
  public record Column(
      String status,
      int currentWip,
      double averageWip,
      Integer limit,
      boolean overLimit,
      BottleneckReason bottleneck) {}

  public record Report(
      LocalDate start,
      LocalDate end,
      List<DailyCounts> cumulativeFlow,
      List<CompletedItem> completions,
      Distribution cycleTime,
      Distribution leadTime,
      double throughputPerDay,
      List<AgingItem> aging,
      List<Column> columns,
      Optional<XmrChart> controlChart,
      LittlesLaw littlesLaw,
      int tasksWithoutHistory) {}

  /**
   * @param start first day of the window (UTC)
   * @param end last day of the window (UTC), inclusive; must not be before {@code start}
   * @param now the evaluation instant; the window is cut off here
   * @param limits WIP limit per column; columns without a limit are absent
   */
  public static Report analyse(
      List<Item> items, LocalDate start, LocalDate end, Instant now, Map<String, Integer> limits) {
    if (end.isBefore(start)) throw new IllegalArgumentException("end before start");
    Instant from = start.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant endExclusive = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant to = endExclusive.isAfter(now) ? now : endExclusive;
    if (to.isBefore(from)) to = from;
    double windowDays = Math.max(Duration.between(from, to).toMillis() / MILLIS_PER_DAY, 1d / 24);

    List<Item> tracked = items.stream().filter(item -> item.timeline().hasHistory()).toList();
    int withoutHistory = items.size() - tracked.size();

    List<DailyCounts> cfd = new ArrayList<>();
    for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
      Instant cutoff = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
      if (cutoff.isAfter(now)) cutoff = now;
      Map<String, Integer> counts = new LinkedHashMap<>();
      for (String status : FlowStatus.ALL) counts.put(status, 0);
      for (Item item : tracked) {
        Instant at = cutoff;
        item.timeline().statusBefore(at).ifPresent(status -> counts.merge(status, 1, Integer::sum));
      }
      cfd.add(new DailyCounts(day, counts));
      if (!cutoff.isBefore(now)) break;
    }

    List<CompletedItem> completions = new ArrayList<>();
    for (Item item : tracked) {
      Optional<TaskTimeline.Completion> completion = item.timeline().completion();
      if (completion.isEmpty()) continue;
      Instant doneAt = completion.get().doneAt();
      if (doneAt.isBefore(from) || !doneAt.isBefore(to)) continue;
      completions.add(
          new CompletedItem(
              item.taskId(),
              doneAt,
              completion.get().cycleTimeHours().orElse(null),
              completion.get().leadTimeHours().orElse(null)));
    }
    completions.sort(
        Comparator.comparing(CompletedItem::doneAt).thenComparingLong(CompletedItem::taskId));
    List<Double> cycles =
        completions.stream()
            .map(CompletedItem::cycleTimeHours)
            .filter(java.util.Objects::nonNull)
            .toList();
    List<Double> leads =
        completions.stream()
            .map(CompletedItem::leadTimeHours)
            .filter(java.util.Objects::nonNull)
            .toList();
    Distribution cycleTime = Distribution.of(cycles);
    Distribution leadTime = Distribution.of(leads);

    List<AgingItem> aging = new ArrayList<>();
    Map<String, Integer> current = new LinkedHashMap<>();
    Map<String, Double> statusMillis = new LinkedHashMap<>();
    for (String status : FlowStatus.ALL) {
      current.put(status, 0);
      statusMillis.put(status, 0d);
    }
    for (Item item : tracked) {
      TaskTimeline timeline = item.timeline();
      String status = timeline.currentStatus().orElseThrow();
      current.merge(status, 1, Integer::sum);
      Instant windowEnd = to;
      timeline
          .timeInStatus(from, windowEnd)
          .forEach((key, value) -> statusMillis.merge(key, (double) value.toMillis(), Double::sum));
      Optional<Instant> since = timeline.activeSince();
      if (since.isPresent()) {
        double age = hoursBetween(since.get(), now);
        double statusAge = hoursBetween(timeline.currentStatusSince().orElseThrow(), now);
        aging.add(new AgingItem(item.taskId(), status, age, statusAge, band(age, cycleTime)));
      }
    }
    aging.sort(
        Comparator.comparingDouble(AgingItem::ageHours)
            .reversed()
            .thenComparingLong(AgingItem::taskId));

    double windowMillis = windowDays * MILLIS_PER_DAY;
    List<Column> columns = new ArrayList<>();
    for (String status : FlowStatus.LIMITABLE) {
      Integer limit = limits.get(status);
      int wip = current.get(status);
      columns.add(
          new Column(
              status, wip, statusMillis.get(status) / windowMillis, limit, limit != null && wip > limit, null));
    }
    columns = markBottleneck(columns);

    double activeMillis = 0;
    for (String status : FlowStatus.ACTIVE) activeMillis += statusMillis.get(status);
    double averageWip = activeMillis / windowMillis;
    double throughput = completions.size() / windowDays;
    double cycleThroughput = cycles.size() / windowDays;
    double meanCycleDays = cycleTime.mean() == null ? 0 : cycleTime.mean() / 24d;
    LittlesLaw littlesLaw =
        LittlesLaw.check(
            averageWip, cycleThroughput, meanCycleDays, cycles.size(), LittlesLaw.DEFAULT_TOLERANCE);

    return new Report(
        start,
        end,
        List.copyOf(cfd),
        List.copyOf(completions),
        cycleTime,
        leadTime,
        throughput,
        List.copyOf(aging),
        columns,
        XmrChart.of(cycles),
        littlesLaw,
        withoutHistory);
  }

  /** Band of {@code ageHours} against the cycle-time percentiles. */
  public static AgeBand band(double ageHours, Distribution cycleTime) {
    if (cycleTime.count() == 0) return AgeBand.NO_REFERENCE;
    if (ageHours <= cycleTime.p50()) return AgeBand.BELOW_P50;
    if (ageHours <= cycleTime.p85()) return AgeBand.P50_TO_P85;
    if (ageHours <= cycleTime.p95()) return AgeBand.P85_TO_P95;
    return AgeBand.ABOVE_P95;
  }

  /**
   * Marks at most one active column. An active column over its limit wins (highest WIP-to-limit
   * ratio, ties in flow order); otherwise the active column with the highest average WIP, if any.
   */
  static List<Column> markBottleneck(List<Column> columns) {
    Column chosen = null;
    BottleneckReason reason = null;
    for (Column column : columns) {
      if (!FlowStatus.active(column.status()) || !column.overLimit()) continue;
      if (chosen == null
          || column.currentWip() * (double) chosen.limit()
              > chosen.currentWip() * (double) column.limit()) chosen = column;
    }
    if (chosen != null) reason = BottleneckReason.OVER_LIMIT;
    else {
      for (Column column : columns) {
        if (!FlowStatus.active(column.status()) || !(column.averageWip() > 0)) continue;
        if (chosen == null || column.averageWip() > chosen.averageWip()) chosen = column;
      }
      if (chosen != null) reason = BottleneckReason.HIGHEST_AVERAGE_WIP;
    }
    List<Column> result = new ArrayList<>(columns.size());
    for (Column column : columns)
      result.add(
          column == chosen
              ? new Column(
                  column.status(),
                  column.currentWip(),
                  column.averageWip(),
                  column.limit(),
                  column.overLimit(),
                  reason)
              : column);
    return List.copyOf(result);
  }

  private static double hoursBetween(Instant start, Instant end) {
    return end.isBefore(start) ? 0 : Duration.between(start, end).toMillis() / 3_600_000d;
  }
}
