package com.sprintflowinsight.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskTimelineTest {
  private static final Instant T0 = Instant.parse("2026-03-02T08:00:00Z");

  static Instant h(double hours) {
    return T0.plusSeconds((long) (hours * 3600));
  }

  static Transition t(String status, double hours) {
    return new Transition(status, h(hours));
  }

  @Test
  void collapsesSnapshotsWithTheSameStatus() {
    TaskTimeline timeline =
        TaskTimeline.of(
            T0,
            List.of(t("To Do", 0), t("To Do", 1), t("In Progress", 2), t("In Progress", 3)));
    assertThat(timeline.transitions()).extracting(Transition::status)
        .containsExactly("To Do", "In Progress");
    assertThat(timeline.currentStatusSince()).contains(h(2));
  }

  @Test
  void cycleTimeCoversTheActiveRunThatEndedInDone() {
    TaskTimeline timeline =
        TaskTimeline.of(
            T0,
            List.of(
                t("To Do", 0),
                t("In Progress", 10),
                t("Review", 20),
                t("In Progress", 22),
                t("Done", 30)));
    TaskTimeline.Completion completion = timeline.completion().orElseThrow();
    assertThat(completion.cycleStart()).isEqualTo(h(10));
    assertThat(completion.cycleTimeHours()).contains(20.0);
    assertThat(completion.leadTimeHours()).contains(30.0);
  }

  @Test
  void reopenedWorkStartsANewCycle() {
    TaskTimeline timeline =
        TaskTimeline.of(
            T0,
            List.of(
                t("To Do", 0),
                t("In Progress", 1),
                t("Done", 5),
                t("In Progress", 50),
                t("Done", 54)));
    assertThat(timeline.completion().orElseThrow().cycleTimeHours()).contains(4.0);
    assertThat(timeline.completion().orElseThrow().leadTimeHours()).contains(54.0);
  }

  @Test
  void straightToDoneHasNoCycleTime() {
    TaskTimeline timeline = TaskTimeline.of(T0, List.of(t("To Do", 0), t("Done", 3)));
    assertThat(timeline.completion().orElseThrow().cycleTimeHours()).isEmpty();
    assertThat(timeline.completion().orElseThrow().leadTimeHours()).contains(3.0);
  }

  @Test
  void leadTimeNeedsAValidCreationTime() {
    assertThat(
            TaskTimeline.of(null, List.of(t("Done", 3))).completion().orElseThrow().leadTimeHours())
        .isEmpty();
    assertThat(
            TaskTimeline.of(h(10), List.of(t("Done", 3)))
                .completion()
                .orElseThrow()
                .leadTimeHours())
        .isEmpty();
  }

  @Test
  void anOpenTaskHasNoCompletionButMayBeActive() {
    TaskTimeline active =
        TaskTimeline.of(T0, List.of(t("To Do", 0), t("In Progress", 2), t("Review", 4)));
    assertThat(active.completion()).isEmpty();
    assertThat(active.activeSince()).contains(h(2));
    assertThat(active.currentStatus()).contains("Review");

    TaskTimeline waiting = TaskTimeline.of(T0, List.of(t("To Do", 0)));
    assertThat(waiting.activeSince()).isEmpty();
    TaskTimeline empty = TaskTimeline.of(T0, List.of());
    assertThat(empty.hasHistory()).isFalse();
    assertThat(empty.currentStatus()).isEmpty();
    assertThat(empty.completion()).isEmpty();
    assertThat(empty.activeSince()).isEmpty();
  }

  @Test
  void statusBeforeAnInstantUsesStrictlyEarlierTransitions() {
    TaskTimeline timeline = TaskTimeline.of(T0, List.of(t("To Do", 0), t("In Progress", 2)));
    assertThat(timeline.statusBefore(h(0))).isEmpty();
    assertThat(timeline.statusBefore(h(2))).contains("To Do");
    assertThat(timeline.statusBefore(h(2.5))).contains("In Progress");
  }

  @Test
  void timeInStatusIsClippedToTheWindow() {
    TaskTimeline timeline =
        TaskTimeline.of(T0, List.of(t("To Do", 0), t("In Progress", 2), t("Done", 6)));
    var time = timeline.timeInStatus(h(1), h(8));
    assertThat(time.get("To Do")).isEqualTo(Duration.ofHours(1));
    assertThat(time.get("In Progress")).isEqualTo(Duration.ofHours(4));
    assertThat(time.get("Done")).isEqualTo(Duration.ofHours(2));
    assertThat(timeline.timeInStatus(h(8), h(8))).isEmpty();
  }

  @Test
  void rejectsTransitionsOutOfOrder() {
    assertThatThrownBy(() -> TaskTimeline.of(T0, List.of(t("Done", 2), t("To Do", 1))))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
