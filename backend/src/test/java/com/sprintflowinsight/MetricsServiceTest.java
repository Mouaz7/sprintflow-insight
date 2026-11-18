package com.sprintflowinsight;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sprintflowinsight.reporting.MetricsService;
import com.sprintflowinsight.workspace.sprint.Sprint;
import com.sprintflowinsight.workspace.sprint.SprintRepository;
import com.sprintflowinsight.workspace.story.UserStoryRepository;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskRepository;
import com.sprintflowinsight.workspace.task.TaskStatusHistory;
import com.sprintflowinsight.workspace.task.TaskStatusHistoryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MetricsServiceTest {
  @Test
  void sprintPaceDoesNotMarkWorkBehindPlanAsOnTrack() {
    var tasks = mock(TaskRepository.class);
    var sprints = mock(SprintRepository.class);
    var history = mock(TaskStatusHistoryRepository.class);
    var stories = mock(UserStoryRepository.class);
    Sprint sprint = new Sprint();
    sprint.setId(70L);
    sprint.setProjectId(10L);
    sprint.setTotalStoryPoints(10);
    sprint.setStartDate(LocalDate.now(ZoneOffset.UTC).minusDays(7));
    sprint.setEndDate(LocalDate.now(ZoneOffset.UTC).plusDays(365));
    when(sprints.findById(70L)).thenReturn(Optional.of(sprint));
    var result = new MetricsService(tasks, sprints, history, stories).summary(10L, 70L);
    assertEquals("Behind", ((Map<?, ?>) result.get("sprintPace")).get("status"));
  }

  @Test
  void calculatesProjectScopedFlowMetricsFromWorkflowTimestamps() {
    TaskRepository tasks = mock(TaskRepository.class);
    SprintRepository sprints = mock(SprintRepository.class);
    TaskStatusHistoryRepository history = mock(TaskStatusHistoryRepository.class);
    Instant now = Instant.now();
    Task done =
        task(
            1L,
            "Done",
            now.minus(72, ChronoUnit.HOURS),
            now.minus(48, ChronoUnit.HOURS),
            now.minus(24, ChronoUnit.HOURS),
            5);
    Task active =
        task(
            2L,
            "In Progress",
            now.minus(8, ChronoUnit.HOURS),
            now.minus(4, ChronoUnit.HOURS),
            null,
            3);
    when(tasks.findByProjectId(10L)).thenReturn(List.of(done, active));
    when(sprints.findByProjectId(10L)).thenReturn(List.of());
    when(history.findByProjectIdOrderByChangedAtDesc(10L))
        .thenReturn(
            List.of(
                history(1L, "Done", now.minus(24, ChronoUnit.HOURS).toString()),
                history(1L, "In Progress", now.minus(48, ChronoUnit.HOURS).toString()),
                history(1L, "To Do", now.minus(72, ChronoUnit.HOURS).toString()),
                history(2L, "In Progress", now.minus(4, ChronoUnit.HOURS).toString()),
                history(2L, "To Do", now.minus(8, ChronoUnit.HOURS).toString())));

    Map<String, Object> metrics = new MetricsService(tasks, sprints, history).summary(10L, null);

    assertEquals(24.0, metrics.get("cycleTimeHours"));
    assertEquals(48.0, metrics.get("leadTimeHours"));
    assertEquals(1L, metrics.get("throughputLast14Days"));
    assertEquals(5L, metrics.get("velocityStoryPoints"));
    assertEquals(1L, metrics.get("workInProgress"));
  }

  @Test
  void selectedSprintThroughputAndVelocityUseItsCompletionHistory() {
    TaskRepository tasks = mock(TaskRepository.class);
    SprintRepository sprints = mock(SprintRepository.class);
    TaskStatusHistoryRepository history = mock(TaskStatusHistoryRepository.class);
    UserStoryRepository stories = mock(UserStoryRepository.class);
    Instant now = Instant.now();
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    Sprint sprint = new Sprint();
    sprint.setId(70L);
    sprint.setProjectId(10L);
    sprint.setStartDate(today.minusDays(7));
    sprint.setEndDate(today.plusDays(7));
    Task completed =
        task(
            7L,
            "Done",
            now.minus(3, ChronoUnit.DAYS),
            now.minus(2, ChronoUnit.DAYS),
            now.minus(30, ChronoUnit.DAYS),
            99);
    completed.setSprintId(70L);
    TaskStatusHistory doneEvent = history(7L, "Done", now.minus(1, ChronoUnit.HOURS).toString());
    doneEvent.setSprintId(70L);
    doneEvent.setStoryPoints(8);
    TaskStatusHistory inProgress =
        history(7L, "In Progress", now.minus(25, ChronoUnit.HOURS).toString());
    inProgress.setSprintId(70L);
    Task completedInAnotherSprint = task(8L, "Done", now.minus(4, ChronoUnit.DAYS), null, now, 13);
    completedInAnotherSprint.setSprintId(70L);
    TaskStatusHistory otherSprintDone =
        history(8L, "Done", now.minus(30, ChronoUnit.MINUTES).toString());
    otherSprintDone.setSprintId(71L);
    otherSprintDone.setStoryPoints(13);
    when(tasks.findByProjectId(10L)).thenReturn(List.of(completed, completedInAnotherSprint));
    when(sprints.findById(70L)).thenReturn(Optional.of(sprint));
    when(sprints.findByProjectId(10L)).thenReturn(List.of());
    when(history.findByProjectIdOrderByChangedAtDesc(10L))
        .thenReturn(List.of(otherSprintDone, doneEvent, inProgress));
    when(stories.findByProjectId(10L)).thenReturn(List.of());

    Map<String, Object> metrics =
        new MetricsService(tasks, sprints, history, stories).summary(10L, 70L);

    assertEquals(1L, metrics.get("throughput"));
    assertEquals(2L, metrics.get("throughputLast14Days"));
    assertEquals(8L, metrics.get("velocityStoryPoints"));
    assertEquals(24.0, metrics.get("cycleTimeHours"));
    assertEquals(71.0, metrics.get("leadTimeHours"));
  }

  @Test
  void leavesDurationsEmptyWhenWorkflowDataIsIncomplete() {
    TaskRepository tasks = mock(TaskRepository.class);
    SprintRepository sprints = mock(SprintRepository.class);
    TaskStatusHistoryRepository history = mock(TaskStatusHistoryRepository.class);
    Task doneWithoutTimestamps = task(3L, "Done", null, null, null, 2);
    when(tasks.findByProjectId(10L)).thenReturn(List.of(doneWithoutTimestamps));
    when(sprints.findByProjectId(10L)).thenReturn(List.of());

    Map<String, Object> metrics = new MetricsService(tasks, sprints, history).summary(10L, null);

    assertNull(metrics.get("cycleTimeHours"));
    assertNull(metrics.get("leadTimeHours"));
  }

  @Test
  void burndownCountsPointsOnlyAfterTaskCreationAndUntilCompletion() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    Task completed =
        task(
            4L,
            "Done",
            today.minusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant(),
            null,
            today.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
            5);
    Task newWork = task(5L, "To Do", today.atStartOfDay(ZoneOffset.UTC).toInstant(), null, null, 3);
    var tasks = mock(TaskRepository.class);
    var sprints = mock(SprintRepository.class);
    var history = mock(TaskStatusHistoryRepository.class);
    when(tasks.findByProjectId(10L)).thenReturn(List.of(completed, newWork));
    var created = history(4L, "To Do", today.minusDays(2) + "T09:00:00Z");
    var done = history(4L, "Done", today.minusDays(1) + "T09:00:00Z");
    var added = history(5L, "To Do", today + "T09:00:00Z");
    added.setStoryPoints(3);
    when(history.findByProjectIdOrderByChangedAtDesc(10L))
        .thenReturn(List.of(added, done, created));
    var summary = new MetricsService(tasks, sprints, history).summary(10L, null);
    List<Map<String, Object>> burndown = (List<Map<String, Object>>) summary.get("burndown");

    assertEquals(14, burndown.size());
    assertEquals(0, burndown.get(10).get("remainingPoints"));
    assertEquals(5, burndown.get(11).get("remainingPoints"));
    assertEquals(0, burndown.get(12).get("remainingPoints"));
    assertEquals(3, burndown.get(13).get("remainingPoints"));
  }

  @Test
  void sprintBurndownUsesTheLatestPersistedWorkflowStateForEachDay() {
    Task item =
        task(
            6L,
            "Done",
            LocalDate.parse("2026-10-01").atStartOfDay(ZoneOffset.UTC).toInstant(),
            null,
            null,
            5);
    TaskStatusHistory created = history(6L, "To Do", "2026-10-01T09:00:00Z");
    TaskStatusHistory completed = history(6L, "Done", "2026-10-03T09:00:00Z");

    List<Map<String, Object>> values =
        MetricsService.burndownFromHistory(
            List.of(item),
            List.of(completed, created),
            LocalDate.parse("2026-10-01"),
            LocalDate.parse("2026-10-03"));

    assertEquals(
        List.of(5, 5, 0), values.stream().map(point -> point.get("remainingPoints")).toList());
  }

  @Test
  void closedSprintBurndownKeepsHistoricalMembershipAndStopsAtClosure() {
    var tasks = mock(TaskRepository.class);
    var sprints = mock(SprintRepository.class);
    var history = mock(TaskStatusHistoryRepository.class);
    Sprint sprint = new Sprint();
    sprint.setId(70L);
    sprint.setProjectId(10L);
    sprint.setStatus("Completed");
    sprint.setStartDate(LocalDate.parse("2026-10-01"));
    sprint.setEndDate(LocalDate.parse("2026-10-05"));
    sprint.setCompletedAt(Instant.parse("2026-10-03T12:00:00Z"));
    Task moved = task(1L, "In Progress", Instant.parse("2026-10-01T08:00:00Z"), null, null, 13);
    moved.setSprintId(71L);
    moved.setTrashedAt(Instant.parse("2026-10-04T12:00:00Z"));
    Task late = task(2L, "To Do", Instant.parse("2026-10-04T08:00:00Z"), null, null, 13);
    late.setSprintId(70L);
    var created = history(1L, "To Do", "2026-10-01T09:00:00Z");
    created.setSprintId(70L);
    var done = history(1L, "Done", "2026-10-03T09:00:00Z");
    done.setSprintId(70L);
    var laterMove = history(1L, "In Progress", "2026-10-04T09:00:00Z");
    laterMove.setSprintId(71L);
    var lateCreated = history(2L, "To Do", "2026-10-04T08:00:00Z");
    lateCreated.setSprintId(70L);
    when(tasks.findByProjectId(10L)).thenReturn(List.of(late));
    when(tasks.findAllByProjectId(10L)).thenReturn(List.of(moved, late));
    when(sprints.findById(70L)).thenReturn(Optional.of(sprint));
    when(history.findByProjectIdOrderByChangedAtDesc(10L))
        .thenReturn(List.of(lateCreated, laterMove, done, created));
    var result =
        new MetricsService(tasks, sprints, history, mock(UserStoryRepository.class))
            .summary(10L, 70L);
    var points = (List<Map<String, Object>>) result.get("burndown");
    assertEquals(
        List.of(5, 5, 0), points.stream().map(point -> point.get("remainingPoints")).toList());
  }

  @Test
  void sprintPaceCountsWorkingDaysAndExcludesTheWeekend() {
    assertEquals(
        2,
        MetricsService.workingDays(LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-05")));
  }

  @Test
  void completedSprintHistoryUsesStateAtClosureInsteadOfCurrentTaskState() {
    TaskRepository tasks = mock(TaskRepository.class);
    SprintRepository sprints = mock(SprintRepository.class);
    TaskStatusHistoryRepository history = mock(TaskStatusHistoryRepository.class);
    Sprint completedSprint = new Sprint();
    completedSprint.setId(70L);
    completedSprint.setProjectId(10L);
    completedSprint.setName("Completed sprint");
    completedSprint.setStatus("Completed");
    completedSprint.setTotalStoryPoints(8);
    completedSprint.setCompletedAt(Instant.parse("2026-10-05T18:00:00Z"));

    // Task 7 was delivered for five points, then moved/reopened after closure.
    TaskStatusHistory delivered = history(7L, "Done", "2026-10-05T12:00:00Z");
    delivered.setSprintId(70L);
    TaskStatusHistory reopenedLater = history(7L, "In Progress", "2026-10-06T10:00:00Z");
    reopenedLater.setSprintId(71L);
    reopenedLater.setStoryPoints(13);
    // Task 8 had not met Done at closure; its later completion must be excluded.
    TaskStatusHistory unfinished = history(8L, "Review", "2026-10-05T13:00:00Z");
    unfinished.setSprintId(70L);
    TaskStatusHistory completedLate = history(8L, "Done", "2026-10-06T12:00:00Z");
    completedLate.setSprintId(70L);
    // Task 9 was reopened before closure, so its earlier Done is not delivery.
    TaskStatusHistory doneEarly = history(9L, "Done", "2026-10-04T12:00:00Z");
    doneEarly.setSprintId(70L);
    TaskStatusHistory reopenedBeforeClosure = history(9L, "In Progress", "2026-10-05T14:00:00Z");
    reopenedBeforeClosure.setSprintId(70L);
    when(tasks.findByProjectId(10L)).thenReturn(List.of());
    when(sprints.findByProjectId(10L)).thenReturn(List.of(completedSprint));
    when(history.findByProjectIdOrderByChangedAtDesc(10L))
        .thenReturn(
            List.of(
                reopenedLater,
                completedLate,
                reopenedBeforeClosure,
                delivered,
                unfinished,
                doneEarly));

    Map<String, Object> metrics = new MetricsService(tasks, sprints, history).summary(10L, null);
    List<?> snapshots = (List<?>) metrics.get("completedSprints");
    assertEquals(1, snapshots.size());
    Map<?, ?> snapshot = (Map<?, ?>) snapshots.get(0);
    assertEquals(5, snapshot.get("actualStoryPoints"));
    assertEquals(1, snapshot.get("completedTasks"));
  }

  @Test
  void completedSprintWithoutClosureTimestampDoesNotFabricateHistoricalMetrics() {
    TaskRepository tasks = mock(TaskRepository.class);
    SprintRepository sprints = mock(SprintRepository.class);
    TaskStatusHistoryRepository history = mock(TaskStatusHistoryRepository.class);
    Sprint incomplete = new Sprint();
    incomplete.setId(70L);
    incomplete.setProjectId(10L);
    incomplete.setName("Incomplete legacy sprint");
    incomplete.setStatus("Completed");
    when(tasks.findByProjectId(10L)).thenReturn(List.of());
    when(sprints.findByProjectId(10L)).thenReturn(List.of(incomplete));
    when(history.findByProjectIdOrderByChangedAtDesc(10L)).thenReturn(List.of());

    Map<String, Object> metrics = new MetricsService(tasks, sprints, history).summary(10L, null);
    assertEquals(List.of(), metrics.get("completedSprints"));
  }

  @Test
  void pointSnapshotsDoNotRestartCompletionOrReviewClocks() {
    TaskRepository tasks = mock(TaskRepository.class);
    SprintRepository sprints = mock(SprintRepository.class);
    TaskStatusHistoryRepository history = mock(TaskStatusHistoryRepository.class);
    Instant now = Instant.now();
    Task done = task(1L, "Done", now.minus(40, ChronoUnit.DAYS), null, null, 8);
    Task review = task(2L, "Review", now.minus(5, ChronoUnit.DAYS), null, null, 3);
    when(tasks.findByProjectId(10L)).thenReturn(List.of(done, review));
    when(sprints.findByProjectId(10L)).thenReturn(List.of());
    when(history.findByProjectIdOrderByChangedAtDesc(10L))
        .thenReturn(
            List.of(
                history(1L, "In Progress", now.minus(35, ChronoUnit.DAYS).toString()),
                history(1L, "In Progress", now.minus(31, ChronoUnit.DAYS).toString()),
                history(
                    1L,
                    "Review",
                    now.minus(31, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS).toString()),
                history(
                    1L,
                    "In Progress",
                    now.minus(31, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS).toString()),
                history(1L, "Done", now.minus(30, ChronoUnit.DAYS).toString()),
                history(1L, "Done", now.minus(1, ChronoUnit.HOURS).toString()),
                history(2L, "Review", now.minus(48, ChronoUnit.HOURS).toString()),
                history(2L, "Review", now.minus(1, ChronoUnit.HOURS).toString())));
    var metrics = new MetricsService(tasks, sprints, history).summary(10L, null);
    assertEquals(0L, metrics.get("throughputLast14Days"));
    assertEquals(120.0, metrics.get("cycleTimeHours"));
    assertEquals(240.0, metrics.get("leadTimeHours"));
    assertEquals(48.0, metrics.get("reviewWaitingHours"));
    verify(history, never()).findByTaskIdOrderByChangedAtDesc(anyLong());
  }

  @Test
  void workingDaysMatchesADailyCalculationAcrossWeekBoundaries() {
    LocalDate base = LocalDate.of(2026, 10, 1);
    for (int offset = 0; offset < 7; offset++) {
      LocalDate start = base.plusDays(offset);
      for (int length = 0; length < 30; length++) {
        LocalDate end = start.plusDays(length);
        long expected =
            start
                .datesUntil(end.plusDays(1))
                .filter(day -> day.getDayOfWeek().getValue() <= 5)
                .count();
        assertEquals(expected, MetricsService.workingDays(start, end));
      }
    }
  }

  private Task task(
      Long id, String status, Instant created, Instant started, Instant completed, int points) {
    Task task = new Task();
    task.setId(id);
    task.setProjectId(10L);
    task.setStatus(status);
    task.setCreatedAt(created);
    task.setStartedAt(started);
    task.setCompletedAt(completed);
    task.setStoryPoints(points);
    return task;
  }

  private TaskStatusHistory history(Long taskId, String status, String changedAt) {
    TaskStatusHistory event = new TaskStatusHistory();
    event.setTaskId(taskId);
    event.setStatus(status);
    event.setChangedAt(Instant.parse(changedAt));
    event.setStoryPoints(5);
    return event;
  }
}
