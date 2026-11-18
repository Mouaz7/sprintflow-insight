package com.sprintflowinsight.flow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The status timeline of one task, built from its persisted transitions.
 *
 * <p>Consecutive rows with the same status (point or sprint snapshots) are collapsed into one
 * transition, so only real workflow changes count. A status lasts from its transition until the
 * next one; the last status lasts until "now" (the end of any window it is measured in).
 */
public final class TaskTimeline {
  private final Instant createdAt;
  private final List<Transition> transitions;

  private TaskTimeline(Instant createdAt, List<Transition> transitions) {
    this.createdAt = createdAt;
    this.transitions = transitions;
  }

  /**
   * @param createdAt task creation; {@code null} when unknown (lead time is then undefined)
   * @param chronological transitions in persisted order: by time, ties in insertion order
   */
  public static TaskTimeline of(Instant createdAt, List<Transition> chronological) {
    List<Transition> collapsed = new ArrayList<>();
    Instant previous = null;
    for (Transition transition : chronological) {
      if (previous != null && transition.at().isBefore(previous))
        throw new IllegalArgumentException("transitions must be in chronological order");
      previous = transition.at();
      if (collapsed.isEmpty() || !Objects.equals(collapsed.getLast().status(), transition.status()))
        collapsed.add(transition);
    }
    return new TaskTimeline(createdAt, List.copyOf(collapsed));
  }

  public List<Transition> transitions() {
    return transitions;
  }

  public boolean hasHistory() {
    return !transitions.isEmpty();
  }

  /** The status in force just before {@code instant}: the latest transition strictly before it. */
  public Optional<String> statusBefore(Instant instant) {
    int low = 0, high = transitions.size();
    while (low < high) {
      int middle = (low + high) >>> 1;
      if (transitions.get(middle).at().isBefore(instant)) low = middle + 1;
      else high = middle;
    }
    return low == 0 ? Optional.empty() : Optional.of(transitions.get(low - 1).status());
  }

  /** A read made at an evaluation instant must not use future imported transitions. */
  public TaskTimeline before(Instant instant) {
    return new TaskTimeline(
        createdAt, transitions.stream().takeWhile(t -> t.at().isBefore(instant)).toList());
  }

  /** The current status according to the history. */
  public Optional<String> currentStatus() {
    return transitions.isEmpty() ? Optional.empty() : Optional.of(transitions.getLast().status());
  }

  /**
   * The completion, when the latest status is Done. Its cycle start is the beginning of the
   * uninterrupted run of active statuses (In Progress, Review) that ended in that Done; a task that
   * went straight to Done has no cycle start.
   */
  public Optional<Completion> completion() {
    int last = transitions.size() - 1;
    if (last < 0 || !FlowStatus.DONE.equals(transitions.get(last).status()))
      return Optional.empty();
    Instant doneAt = transitions.get(last).at();
    Instant start = activeRunStartBefore(last);
    return Optional.of(new Completion(doneAt, start, createdAt));
  }

  /**
   * When the task is currently active, the start of its uninterrupted active run; otherwise empty.
   */
  public Optional<Instant> activeSince() {
    int last = transitions.size() - 1;
    if (last < 0 || !FlowStatus.active(transitions.get(last).status())) return Optional.empty();
    return Optional.of(activeRunStartBefore(last + 1));
  }

  /** When the current status was entered. */
  public Optional<Instant> currentStatusSince() {
    return transitions.isEmpty() ? Optional.empty() : Optional.of(transitions.getLast().at());
  }

  /**
   * Time spent in each status within {@code [from, to)}. The last status is taken to last until
   * {@code to}.
   */
  public Map<String, Duration> timeInStatus(Instant from, Instant to) {
    Map<String, Duration> result = new LinkedHashMap<>();
    if (!to.isAfter(from)) return result;
    for (int i = 0; i < transitions.size(); i++) {
      Instant start = transitions.get(i).at();
      Instant end = i + 1 < transitions.size() ? transitions.get(i + 1).at() : to;
      Instant a = start.isAfter(from) ? start : from;
      Instant b = end.isBefore(to) ? end : to;
      if (b.isAfter(a))
        result.merge(transitions.get(i).status(), Duration.between(a, b), Duration::plus);
    }
    return result;
  }

  private Instant activeRunStartBefore(int index) {
    Instant start = null;
    for (int i = index - 1; i >= 0 && FlowStatus.active(transitions.get(i).status()); i--)
      start = transitions.get(i).at();
    return start;
  }

  /**
   * A finished task.
   *
   * @param doneAt the transition into Done
   * @param cycleStart start of the active run that ended in Done; {@code null} if there was none
   * @param createdAt task creation; {@code null} when unknown
   */
  public record Completion(Instant doneAt, Instant cycleStart, Instant createdAt) {
    /** Hours from {@code cycleStart} to {@code doneAt}, when defined. */
    public Optional<Double> cycleTimeHours() {
      return hours(cycleStart, doneAt);
    }

    /** Hours from creation to {@code doneAt}, when defined and non-negative. */
    public Optional<Double> leadTimeHours() {
      return hours(createdAt, doneAt);
    }

    private static Optional<Double> hours(Instant start, Instant end) {
      if (start == null || end.isBefore(start)) return Optional.empty();
      return Optional.of(Duration.between(start, end).toMillis() / 3_600_000d);
    }
  }
}
