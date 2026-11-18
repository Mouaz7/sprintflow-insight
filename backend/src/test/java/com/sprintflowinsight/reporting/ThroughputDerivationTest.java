package com.sprintflowinsight.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.forecast.ThroughputUnit;
import com.sprintflowinsight.reporting.ThroughputDerivation.Derived;
import com.sprintflowinsight.reporting.ThroughputDerivation.Period;
import com.sprintflowinsight.reporting.ThroughputDerivation.SprintRecord;
import com.sprintflowinsight.reporting.ThroughputDerivation.StatusEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ThroughputDerivationTest {
  // Wednesday; the current ISO week starts on Monday 2026-11-16.
  private static final LocalDate TODAY = LocalDate.parse("2026-11-18");
  private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

  @Test
  void countsATaskOnceAtItsLatestRealDoneTransition() {
    List<StatusEvent> events =
        List.of(
            event(1, 1, "Done", "2026-11-02T10:00:00Z"),
            event(1, 2, "In Progress", "2026-11-03T10:00:00Z"),
            event(1, 3, "Done", "2026-11-04T10:00:00Z"),
            event(1, 8, "Done", "2026-11-17T10:00:00Z"),
            event(2, 4, "Done", "2026-11-05T10:00:00Z"),
            event(2, 5, "Review", "2026-11-05T10:00:00Z"),
            event(3, 6, "Done", "2026-11-06T10:00:00Z"),
            event(4, 7, "Done", "2026-11-07T10:00:00Z"));

    // Task 2's latest snapshot (same instant, higher id) is Review; task 4 is not done now.
    assertThat(ThroughputDerivation.completions(Set.of(1L, 2L, 3L), events))
        .containsExactly(
            Instant.parse("2026-11-04T10:00:00Z"), Instant.parse("2026-11-06T10:00:00Z"));
  }

  @Test
  void ignoresEventsWithoutATimestamp() {
    List<StatusEvent> events =
        List.of(
            event(1, 1, "Done", "2026-11-02T10:00:00Z"),
            new StatusEvent(1, 2, "To Do", null, null, 0));
    assertThat(ThroughputDerivation.completions(Set.of(1L), events)).hasSize(1);
  }

  @Test
  void binsFullIsoWeeksIncludingEmptyOnesAndLeavesOutTheCurrentWeek() {
    List<Instant> completions =
        List.of(
            Instant.parse("2026-10-26T00:00:00Z"), // Monday, week 1
            Instant.parse("2026-11-01T23:59:59Z"), // Sunday, still week 1
            Instant.parse("2026-11-09T08:00:00Z"), // week 3 (week 2 is empty)
            Instant.parse("2026-11-16T08:00:00Z")); // current week: excluded

    Derived derived = ThroughputDerivation.weekly(completions, TODAY);

    assertThat(derived.history().counts()).containsExactly(2, 0, 1);
    assertThat(derived.history().periodDays()).isEqualTo(7);
    assertThat(derived.history().unit()).isEqualTo(ThroughputUnit.ITEMS);
    assertThat(derived.periods())
        .extracting(Period::start)
        .containsExactly(
            LocalDate.parse("2026-10-26"),
            LocalDate.parse("2026-11-02"),
            LocalDate.parse("2026-11-09"));
    assertThat(derived.periods().get(0).end()).isEqualTo(LocalDate.parse("2026-11-01"));
    assertThat(derived.periods().get(0).label()).isNull();
  }

  @Test
  void hasNoHistoryWhenNothingFinishedBeforeThisWeek() {
    assertThat(ThroughputDerivation.weekly(List.of(), TODAY).history().size()).isZero();
    Derived onlyThisWeek =
        ThroughputDerivation.weekly(List.of(Instant.parse("2026-11-16T00:00:00Z")), TODAY);
    assertThat(onlyThisWeek.history().size()).isZero();
    assertThat(onlyThisWeek.periods()).isEmpty();
  }

  @Test
  void includesLeadingAndEntirelyEmptyObservedWeeks() {
    var since = Instant.parse("2026-10-26T00:00:00Z");
    var completed = List.of(Instant.parse("2026-11-10T08:00:00Z"));
    assertThat(ThroughputDerivation.weekly(completed, TODAY, since).history().counts())
        .containsExactly(0, 0, 1);
    assertThat(ThroughputDerivation.weekly(List.of(), TODAY, since).history().counts())
        .containsExactly(0, 0, 0);
    assertThat(
            ThroughputDerivation.weekly(completed, TODAY, since.plusSeconds(1)).history().counts())
        .containsExactly(0, 1);
  }

  @Test
  void keepsOnlyTheLatestTwoYearsOfWeeks() {
    List<Instant> completions = new ArrayList<>();
    completions.add(Instant.parse("2020-01-06T00:00:00Z"));
    completions.add(Instant.parse("2026-11-10T00:00:00Z"));

    Derived derived = ThroughputDerivation.weekly(completions, TODAY);

    assertThat(derived.history().size()).isEqualTo(ThroughputDerivation.MAX_WEEKS);
    assertThat(derived.history().total(0, derived.history().size())).isEqualTo(1);
    assertThat(derived.periods().get(derived.periods().size() - 1).start())
        .isEqualTo(LocalDate.parse("2026-11-09"));
  }

  @Test
  void velocityReplaysDeliveredPointsAtEachSprintsClosure() {
    SprintRecord first = sprint(10, "S1", "2026-10-05", "2026-10-18", "2026-10-18T16:00:00Z");
    SprintRecord second = sprint(11, "S2", "2026-10-19", "2026-11-01", "2026-11-01T16:00:00Z");
    List<StatusEvent> events =
        List.of(
            // Done in S1 before closure: counts for S1.
            new StatusEvent(1, 1, "Done", 10L, Instant.parse("2026-10-17T10:00:00Z"), 5),
            // Reopened after S1 closed: S1 keeps its points.
            new StatusEvent(1, 2, "In Progress", 10L, Instant.parse("2026-10-20T10:00:00Z"), 5),
            // Done in S2.
            new StatusEvent(2, 3, "Done", 11L, Instant.parse("2026-10-25T10:00:00Z"), 3),
            // Done for S2 after its closure: not delivered by S2.
            new StatusEvent(3, 4, "Done", 11L, Instant.parse("2026-11-02T10:00:00Z"), 8),
            // Done, but recorded for another sprint.
            new StatusEvent(4, 5, "Done", 99L, Instant.parse("2026-10-25T10:00:00Z"), 13),
            new StatusEvent(5, 6, "Done", 11L, Instant.parse("2026-10-26T10:00:00Z"), -2));

    Derived derived = ThroughputDerivation.velocity(List.of(second, first), events, NOW);

    assertThat(derived.history().counts()).containsExactly(5, 3);
    assertThat(derived.history().unit()).isEqualTo(ThroughputUnit.STORY_POINTS);
    assertThat(derived.history().periodDays()).isEqualTo(14);
    assertThat(derived.periods()).extracting(Period::label).containsExactly("S1", "S2");
    assertThat(derived.periods().get(1).start()).isEqualTo(LocalDate.parse("2026-10-19"));
  }

  @Test
  void velocityWithholdsSprintsWithoutAValidClosure() {
    List<SprintRecord> sprints =
        List.of(
            new SprintRecord(1, "Active", "Active", null, null, null, null),
            new SprintRecord(2, "No closure", "Completed", null, null, null, null),
            new SprintRecord(
                3, "Future", "Completed", null, null, null, Instant.parse("2027-01-01T00:00:00Z")),
            new SprintRecord(
                4,
                "Closed before created",
                "Completed",
                null,
                null,
                Instant.parse("2026-10-02T00:00:00Z"),
                Instant.parse("2026-10-01T00:00:00Z")),
            new SprintRecord(
                5,
                "Undated",
                "Completed",
                null,
                null,
                null,
                Instant.parse("2026-10-10T09:00:00Z")));

    Derived derived = ThroughputDerivation.velocity(sprints, List.of(), NOW);

    assertThat(derived.periods()).extracting(Period::label).containsExactly("Undated");
    assertThat(derived.periods().get(0).start()).isEqualTo(LocalDate.parse("2026-10-10"));
    assertThat(derived.periods().get(0).end()).isEqualTo(LocalDate.parse("2026-10-10"));
    assertThat(derived.history().periodDays()).isEqualTo(ThroughputDerivation.DEFAULT_SPRINT_DAYS);
  }

  @Test
  void velocityWithoutCompletedSprintsIsEmpty() {
    Derived derived = ThroughputDerivation.velocity(List.of(), List.of(), NOW);
    assertThat(derived.history().size()).isZero();
    assertThat(derived.history().unit()).isEqualTo(ThroughputUnit.STORY_POINTS);
  }

  @Test
  void velocityKeepsTheLatestSprints() {
    List<SprintRecord> sprints = new ArrayList<>();
    for (int i = 0; i < ThroughputDerivation.MAX_SPRINTS + 3; i++) {
      LocalDate end = LocalDate.parse("2024-01-14").plusWeeks(i);
      sprints.add(
          sprint(i, "S" + i, end.minusDays(6).toString(), end.toString(), end + "T12:00:00Z"));
    }
    Derived derived = ThroughputDerivation.velocity(sprints, List.of(), NOW);
    assertThat(derived.history().size()).isEqualTo(ThroughputDerivation.MAX_SPRINTS);
    assertThat(derived.periods().get(0).label()).isEqualTo("S3");
    assertThat(derived.history().periodDays()).isEqualTo(7);
  }

  @Test
  void typicalSprintLengthIsTheLowerMedianOfValidSchedules() {
    assertThat(
            ThroughputDerivation.typicalSprintDays(
                List.of(
                    sprint(1, "a", "2026-01-01", "2026-01-07", "2026-01-07T00:00:00Z"),
                    sprint(2, "b", "2026-01-01", "2026-01-14", "2026-01-14T00:00:00Z"),
                    sprint(3, "c", "2026-01-01", "2026-01-21", "2026-01-21T00:00:00Z"),
                    sprint(4, "d", "2026-01-01", "2026-01-28", "2026-01-28T00:00:00Z"),
                    sprint(5, "reversed", "2026-02-01", "2026-01-01", "2026-02-01T00:00:00Z"))))
        .isEqualTo(14);
    assertThat(
            ThroughputDerivation.typicalSprintDays(
                List.of(sprint(1, "one day", "2026-01-01", "2026-01-01", "2026-01-01T00:00:00Z"))))
        .isEqualTo(1);
    assertThat(
            ThroughputDerivation.typicalSprintDays(
                List.of(sprint(1, "long", "2020-01-01", "2026-01-01", "2026-01-01T00:00:00Z"))))
        .isEqualTo(366);
  }

  private static StatusEvent event(long task, long id, String status, String at) {
    return new StatusEvent(task, id, status, null, Instant.parse(at), 1);
  }

  private static SprintRecord sprint(
      long id, String name, String start, String end, String completedAt) {
    return new SprintRecord(
        id,
        name,
        "Completed",
        LocalDate.parse(start),
        LocalDate.parse(end),
        Instant.parse("2020-01-01T00:00:00Z"),
        Instant.parse(completedAt));
  }
}
