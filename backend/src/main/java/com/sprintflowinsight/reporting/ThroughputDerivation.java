package com.sprintflowinsight.reporting;

import com.sprintflowinsight.forecast.ThroughputHistory;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Turns persisted workflow history into forecast inputs. Pure: callers pass plain records, so the
 * rules can be tested without a database. Weeks are ISO weeks (Monday to Sunday) in UTC, matching
 * the other delivery metrics.
 */
final class ThroughputDerivation {
  /** Older weeks are dropped: two years is ample for every model and bounds the work. */
  static final int MAX_WEEKS = 104;

  /** At most this many of the latest completed sprints are used. */
  static final int MAX_SPRINTS = 52;

  /** Sprint length assumed when no completed sprint has valid schedule dates. */
  static final int DEFAULT_SPRINT_DAYS = 14;

  static final String DONE = "Done";
  static final String COMPLETED = "Completed";

  private static final Comparator<StatusEvent> EVENT_ORDER =
      Comparator.comparing(StatusEvent::changedAt).thenComparingLong(StatusEvent::id);

  private ThroughputDerivation() {}

  /** One persisted status snapshot of a task. */
  record StatusEvent(
      long taskId, long id, String status, Long sprintId, Instant changedAt, int storyPoints) {}

  /** The sprint fields the velocity history needs. */
  record SprintRecord(
      long id,
      String name,
      String status,
      LocalDate startDate,
      LocalDate endDate,
      Instant createdAt,
      Instant completedAt) {}

  /** One history period as shown to users; {@code label} is the sprint name for velocity. */
  record Period(LocalDate start, LocalDate end, int count, String label) {}

  /** A forecast history together with the calendar periods it was built from. */
  record Derived(ThroughputHistory history, List<Period> periods) {
    Derived {
      periods = List.copyOf(periods);
    }

    static Derived empty(int periodDays, com.sprintflowinsight.forecast.ThroughputUnit unit) {
      return new Derived(ThroughputHistory.of(periodDays, unit), List.of());
    }
  }

  /**
   * Completion instants of the tasks that are done now: the time of each task's latest status
   * snapshot, provided that snapshot is {@code Done}. This is the same rule as the throughput
   * metric, so a task reopened and completed again counts once, at its latest completion.
   */
  static List<Instant> completions(Set<Long> doneTaskIds, Collection<StatusEvent> events) {
    Map<Long, StatusEvent> latest = new HashMap<>();
    for (StatusEvent event : events) {
      if (event.changedAt() == null || !doneTaskIds.contains(event.taskId())) continue;
      latest.merge(
          event.taskId(), event, (a, b) -> EVENT_ORDER.compare(a, b) >= 0 ? a : b);
    }
    return latest.values().stream()
        .filter(event -> DONE.equals(event.status()))
        .map(StatusEvent::changedAt)
        .sorted()
        .toList();
  }

  /**
   * Items completed per full ISO week, from the week of the first completion to the last week
   * that has ended before {@code today}. The current, unfinished week is left out because it would
   * look like a slow week. Weeks without completions count as zero.
   */
  static Derived weekly(List<Instant> completions, LocalDate today) {
    LocalDate currentWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate first = null;
    for (Instant completion : completions) {
      LocalDate week = weekOf(completion);
      if (first == null || week.isBefore(first)) first = week;
    }
    if (first == null || !first.isBefore(currentWeek)) {
      return Derived.empty(7, com.sprintflowinsight.forecast.ThroughputUnit.ITEMS);
    }
    long weeks = ChronoUnit.WEEKS.between(first, currentWeek);
    if (weeks > MAX_WEEKS) {
      first = currentWeek.minusWeeks(MAX_WEEKS);
      weeks = MAX_WEEKS;
    }
    int[] counts = new int[(int) weeks];
    for (Instant completion : completions) {
      long index = ChronoUnit.WEEKS.between(first, weekOf(completion));
      if (index >= 0 && index < weeks) counts[(int) index]++;
    }
    List<Period> periods = new ArrayList<>(counts.length);
    for (int i = 0; i < counts.length; i++) {
      LocalDate start = first.plusWeeks(i);
      periods.add(new Period(start, start.plusDays(6), counts[i], null));
    }
    return new Derived(ThroughputHistory.weekly(counts), periods);
  }

  /**
   * Story points delivered per completed sprint, oldest first, replayed at each sprint's actual
   * closure exactly like the completed-sprint history in the metrics view. Sprints with a missing,
   * future or inconsistent closure time are left out rather than given invented values.
   */
  static Derived velocity(
      Collection<SprintRecord> sprints, Collection<StatusEvent> events, Instant now) {
    List<SprintRecord> completed =
        sprints.stream()
            .filter(sprint -> COMPLETED.equals(sprint.status()))
            .filter(sprint -> sprint.completedAt() != null && !sprint.completedAt().isAfter(now))
            .filter(
                sprint ->
                    sprint.createdAt() == null
                        || !sprint.completedAt().isBefore(sprint.createdAt()))
            .sorted(
                Comparator.comparing(SprintRecord::completedAt)
                    .thenComparingLong(SprintRecord::id))
            .toList();
    if (completed.size() > MAX_SPRINTS) {
      completed = completed.subList(completed.size() - MAX_SPRINTS, completed.size());
    }
    int periodDays = typicalSprintDays(completed);
    if (completed.isEmpty()) {
      return Derived.empty(periodDays, com.sprintflowinsight.forecast.ThroughputUnit.STORY_POINTS);
    }
    Map<Long, List<StatusEvent>> byTask = new HashMap<>();
    for (StatusEvent event : events) {
      if (event.changedAt() == null) continue;
      byTask.computeIfAbsent(event.taskId(), id -> new ArrayList<>()).add(event);
    }
    byTask.values().forEach(list -> list.sort(EVENT_ORDER));
    int[] points = new int[completed.size()];
    List<Period> periods = new ArrayList<>(completed.size());
    for (int i = 0; i < completed.size(); i++) {
      SprintRecord sprint = completed.get(i);
      points[i] = deliveredAtClosure(sprint, byTask.values());
      LocalDate closed = sprint.completedAt().atZone(ZoneOffset.UTC).toLocalDate();
      periods.add(
          new Period(
              sprint.startDate() == null ? closed : sprint.startDate(),
              sprint.endDate() == null ? closed : sprint.endDate(),
              points[i],
              sprint.name()));
    }
    return new Derived(ThroughputHistory.sprintVelocity(periodDays, points), periods);
  }

  /** Median scheduled length in days (start and end inclusive) of sprints with valid dates. */
  static int typicalSprintDays(List<SprintRecord> sprints) {
    List<Long> lengths =
        sprints.stream()
            .filter(sprint -> sprint.startDate() != null && sprint.endDate() != null)
            .filter(sprint -> !sprint.endDate().isBefore(sprint.startDate()))
            .map(sprint -> ChronoUnit.DAYS.between(sprint.startDate(), sprint.endDate()) + 1)
            .sorted()
            .toList();
    if (lengths.isEmpty()) return DEFAULT_SPRINT_DAYS;
    return (int) Math.min(366, lengths.get((lengths.size() - 1) / 2));
  }

  private static int deliveredAtClosure(
      SprintRecord sprint, Collection<List<StatusEvent>> sortedByTask) {
    int sum = 0;
    for (List<StatusEvent> events : sortedByTask) {
      StatusEvent atClosure = null;
      for (StatusEvent event : events) {
        if (event.changedAt().isAfter(sprint.completedAt())) break;
        atClosure = event;
      }
      if (atClosure != null
          && DONE.equals(atClosure.status())
          && Objects.equals(sprint.id(), atClosure.sprintId())) {
        sum += Math.max(0, atClosure.storyPoints());
      }
    }
    return sum;
  }

  private static LocalDate weekOf(Instant instant) {
    return instant
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }
}
