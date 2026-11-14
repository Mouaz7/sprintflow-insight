package com.sprintflowinsight.service;

import com.sprintflowinsight.model.Sprint;
import com.sprintflowinsight.model.Task;
import com.sprintflowinsight.model.TaskStatusHistory;
import com.sprintflowinsight.repository.SprintRepository;
import com.sprintflowinsight.repository.TaskRepository;
import com.sprintflowinsight.repository.TaskStatusHistoryRepository;
import com.sprintflowinsight.story.UserStory;
import com.sprintflowinsight.story.UserStoryRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {
  private final TaskRepository tasks;
  private final SprintRepository sprints;
  private final TaskStatusHistoryRepository history;
  private final UserStoryRepository stories;

  @Autowired
  public MetricsService(
      TaskRepository tasks,
      SprintRepository sprints,
      TaskStatusHistoryRepository history,
      UserStoryRepository stories) {
    this.tasks = tasks;
    this.sprints = sprints;
    this.history = history;
    this.stories = stories;
  }

  public MetricsService(
      TaskRepository tasks, SprintRepository sprints, TaskStatusHistoryRepository history) {
    this(tasks, sprints, history, null);
  }

  public Map<String, Object> summary(Long projectId, Long sprintId) {
    List<Task> scoped = tasks.findByProjectId(projectId);
    if (sprintId != null)
      scoped = scoped.stream().filter(t -> sprintId.equals(t.getSprintId())).toList();
    List<Task> done = scoped.stream().filter(t -> "Done".equals(t.getStatus())).toList();
    List<TaskStatusHistory> statusHistory = history.findByProjectIdOrderByChangedAtDesc(projectId);
    Map<Long, List<TaskStatusHistory>> historyByTask =
        statusHistory.stream()
            .filter(event -> event.getTaskId() != null)
            .collect(Collectors.groupingBy(TaskStatusHistory::getTaskId));
    Map<Long, List<TaskStatusHistory>> transitionsByTask =
        historyByTask.entrySet().stream()
            .collect(
                Collectors.toMap(Map.Entry::getKey, entry -> statusTransitions(entry.getValue())));
    List<CompletedTask> completedWithHistory = completedWithLatestHistory(done, transitionsByTask);
    Instant now = Instant.now();
    Instant since = now.minus(14, ChronoUnit.DAYS);
    long throughputLast14Days =
        completedWithHistory.stream()
            .filter(item -> within(item.completedAt(), since, now))
            .count();
    Sprint selectedSprint =
        sprintId == null
            ? null
            : sprints
                .findById(sprintId)
                .filter(sprint -> projectId.equals(sprint.getProjectId()))
                .orElse(null);
    List<CompletedTask> selectedCompletions =
        sprintId == null
            ? completedWithHistory
            : completedWithHistory.stream()
                .filter(item -> belongsToSprint(item, sprintId, selectedSprint))
                .toList();
    long scopedThroughput = sprintId == null ? throughputLast14Days : selectedCompletions.size();
    long wip =
        scoped.stream()
            .filter(t -> "In Progress".equals(t.getStatus()) || "Review".equals(t.getStatus()))
            .count();
    long blocked = scoped.stream().filter(Task::isBlocked).count();
    long velocity =
        selectedCompletions.stream().mapToLong(item -> item.completedAt().getStoryPoints()).sum();
    long overdue =
        scoped.stream()
            .filter(
                task ->
                    task.getDueAt() != null
                        && task.getDueAt().isBefore(Instant.now())
                        && !"Done".equals(task.getStatus()))
            .count();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("cycleTimeHours", averageCycleTimeHours(selectedCompletions, transitionsByTask));
    result.put("leadTimeHours", averageLeadTimeHours(selectedCompletions));
    result.put("throughputLast14Days", throughputLast14Days);
    result.put("throughput", scopedThroughput);
    result.put("completedTasks", done.size());
    result.put("workInProgress", wip);
    result.put("blockedTasks", blocked);
    result.put("reviewWaitingHours", reviewWaitingHours(scoped, transitionsByTask));
    result.put("velocityStoryPoints", velocity);
    result.put("taskCount", scoped.size());
    result.put("overdueTasks", overdue);
    result.put(
        "completionRate", scoped.isEmpty() ? 0 : Math.round(done.size() * 100d / scoped.size()));
    result.put(
        "statusCounts",
        scoped.stream()
            .collect(
                Collectors.groupingBy(Task::getStatus, LinkedHashMap::new, Collectors.counting())));
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    LocalDate burndownStart = today.minusDays(13);
    LocalDate burndownEnd = today;
    boolean burndownAvailable = true;
    Instant closure = null;
    if (sprintId != null) {
      if (selectedSprint == null
          || selectedSprint.getStartDate() == null
          || selectedSprint.getEndDate() == null) {
        burndownAvailable = false;
      } else {
        burndownStart = selectedSprint.getStartDate();
        burndownEnd =
            selectedSprint.getEndDate().isBefore(today) ? selectedSprint.getEndDate() : today;
        if ("Completed".equals(selectedSprint.getStatus())) {
          closure = selectedSprint.getCompletedAt();
          if (closure == null
              || closure.isAfter(now)
              || (selectedSprint.getCreatedAt() != null
                  && closure.isBefore(selectedSprint.getCreatedAt()))) {
            burndownAvailable = false;
          } else {
            LocalDate closedDay = closure.atZone(ZoneOffset.UTC).toLocalDate();
            if (closedDay.isBefore(burndownEnd)) burndownEnd = closedDay;
          }
        }
        if (burndownEnd.isBefore(burndownStart)) burndownAvailable = false;
      }
    }
    // Replay membership at the event time. Current sprint membership would erase
    // earlier work when tasks move, or count work before it joined this sprint.
    Instant chartClosure = closure;
    List<Task> chartTasks =
        sprintId == null
            ? scoped
            : tasks.findAllByProjectId(projectId).stream()
                .filter(
                    task ->
                        chartClosure == null
                            || task.getCreatedAt() == null
                            || !task.getCreatedAt().isAfter(chartClosure))
                .filter(
                    task ->
                        sprintId.equals(task.getSprintId())
                            || historyByTask.getOrDefault(task.getId(), List.of()).stream()
                                .anyMatch(event -> sprintId.equals(event.getSprintId())))
                .toList();
    List<Map<String, Object>> burndown =
        burndownAvailable
            ? burndownFromHistory(
                chartTasks, statusHistory, burndownStart, burndownEnd, sprintId, closure)
            : List.of();
    boolean hasHistoryForAllTasks =
        chartTasks.stream().allMatch(task -> historyByTask.containsKey(task.getId()));
    burndownAvailable = burndownAvailable && !chartTasks.isEmpty() && hasHistoryForAllTasks;
    result.put("burndown", burndownAvailable ? burndown : List.of());
    result.put("burndownAvailable", burndownAvailable);
    result.put(
        "wipByStatus",
        scoped.stream()
            .filter(t -> "In Progress".equals(t.getStatus()) || "Review".equals(t.getStatus()))
            .collect(Collectors.groupingBy(Task::getStatus, Collectors.counting())));
    result.put(
        "sprintHealth",
        health(scoped, blocked, wip, reviewWaitingHours(scoped, transitionsByTask)));
    result.put(
        "completedSprints",
        sprints.findByProjectId(projectId).stream()
            .filter(sprint -> "Completed".equals(sprint.getStatus()))
            .filter(
                sprint -> sprint.getCompletedAt() != null && !sprint.getCompletedAt().isAfter(now))
            .filter(
                sprint ->
                    sprint.getCreatedAt() == null
                        || !sprint.getCompletedAt().isBefore(sprint.getCreatedAt()))
            .map(sprint -> sprintSnapshot(sprint, historyByTask))
            .toList());
    List<Task> metricScope = scoped;
    if (selectedSprint != null) {
      Sprint sprint = selectedSprint;
      result.put("sprintPace", sprintPace(sprint, metricScope, selectedCompletions));
      result.put("businessValue", businessValue(sprint, metricScope));
    }
    return result;
  }

  private List<CompletedTask> completedWithLatestHistory(
      List<Task> done, Map<Long, List<TaskStatusHistory>> historyByTask) {
    return done.stream()
        .map(
            task -> {
              TaskStatusHistory latest =
                  historyByTask.getOrDefault(task.getId(), List.of()).stream()
                      .filter(event -> event.getChangedAt() != null)
                      .max(
                          java.util.Comparator.comparing(TaskStatusHistory::getChangedAt)
                              .thenComparing(
                                  event -> event.getId() == null ? Long.MIN_VALUE : event.getId()))
                      .orElse(null);
              return latest != null && "Done".equals(latest.getStatus())
                  ? new CompletedTask(task, latest)
                  : null;
            })
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  private boolean belongsToSprint(CompletedTask item, Long sprintId, Sprint sprint) {
    TaskStatusHistory event = item.completedAt();
    if (!sprintId.equals(event.getSprintId())) return false;
    if (sprint == null || sprint.getStartDate() == null || sprint.getEndDate() == null) return true;
    Instant start = sprint.getStartDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant endExclusive = sprint.getEndDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    return !event.getChangedAt().isBefore(start) && event.getChangedAt().isBefore(endExclusive);
  }

  private boolean within(TaskStatusHistory event, Instant start, Instant end) {
    return event.getChangedAt() != null
        && !event.getChangedAt().isBefore(start)
        && !event.getChangedAt().isAfter(end);
  }

  private Double averageCycleTimeHours(
      List<CompletedTask> completed, Map<Long, List<TaskStatusHistory>> historyByTask) {
    List<Long> minutes =
        completed.stream()
            .map(
                item ->
                    historyByTask.getOrDefault(item.task().getId(), List.of()).stream()
                        .filter(
                            event ->
                                "In Progress".equals(event.getStatus())
                                    && event.getChangedAt() != null
                                    && !event
                                        .getChangedAt()
                                        .isAfter(item.completedAt().getChangedAt()))
                        .max(EVENT_ORDER)
                        .map(
                            event ->
                                validMinutes(
                                    event.getChangedAt(), item.completedAt().getChangedAt()))
                        .orElse(null))
            .filter(java.util.Objects::nonNull)
            .toList();
    return averageHours(minutes);
  }

  private Double averageLeadTimeHours(List<CompletedTask> completed) {
    return averageHours(
        completed.stream()
            .map(
                item -> validMinutes(item.task().getCreatedAt(), item.completedAt().getChangedAt()))
            .filter(java.util.Objects::nonNull)
            .toList());
  }

  private Long validMinutes(Instant start, Instant end) {
    return start == null || end == null || end.isBefore(start)
        ? null
        : Duration.between(start, end).toMinutes();
  }

  private Double averageHours(List<Long> values) {
    if (values.isEmpty()) return null;
    return Math.round((values.stream().mapToLong(Long::longValue).average().orElse(0) / 60d) * 10d)
        / 10d;
  }

  private record CompletedTask(Task task, TaskStatusHistory completedAt) {}

  private Map<String, Object> sprintPace(
      Sprint sprint, List<Task> scoped, List<CompletedTask> completed) {
    int planned = sprint.getTotalStoryPoints();
    int delivered = completed.stream().mapToInt(item -> item.completedAt().getStoryPoints()).sum();
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    if (sprint.getStartDate() == null
        || sprint.getEndDate() == null
        || !sprint.getEndDate().isAfter(sprint.getStartDate())) {
      return Map.of(
          "available",
          false,
          "plannedStoryPoints",
          planned,
          "deliveredStoryPoints",
          delivered,
          "status",
          "Schedule dates required");
    }
    long duration = workingDays(sprint.getStartDate(), sprint.getEndDate());
    long elapsed =
        workingDays(
            sprint.getStartDate(),
            today.isAfter(sprint.getEndDate()) ? sprint.getEndDate() : today);
    duration = Math.max(1, duration);
    elapsed = Math.min(duration, elapsed);
    double expected = planned * (elapsed / (double) duration);
    String status = delivered >= expected ? "On track" : "Behind";
    return Map.of(
        "available",
        true,
        "plannedStoryPoints",
        planned,
        "deliveredStoryPoints",
        delivered,
        "expectedStoryPoints",
        Math.round(expected * 10d) / 10d,
        "status",
        status,
        "elapsedPercent",
        Math.round(elapsed * 100d / duration));
  }

  private Map<String, Object> businessValue(Sprint sprint, List<Task> scoped) {
    List<UserStory> valuedStories =
        stories.findByProjectId(sprint.getProjectId()).stream()
            .filter(story -> sprint.getId().equals(story.getSprintId()))
            .filter(story -> story.getBusinessValue() != null)
            .toList();
    int expected = valuedStories.stream().mapToInt(UserStory::getBusinessValue).sum();
    int delivered = (int) valuedStories.stream().filter(story -> storyDone(story, scoped)).count();
    int doneValue =
        valuedStories.stream()
            .filter(story -> storyDone(story, scoped))
            .mapToInt(UserStory::getBusinessValue)
            .sum();
    return Map.of(
        "available",
        !valuedStories.isEmpty(),
        "scoredStories",
        valuedStories.size(),
        "expectedValue",
        expected,
        "deliveredValue",
        doneValue,
        "deliveredStories",
        delivered,
        "deliveredPercent",
        expected == 0 ? 0 : Math.round(doneValue * 100d / expected));
  }

  private boolean storyDone(UserStory story, List<Task> sprintTasks) {
    List<Task> storyTasks =
        sprintTasks.stream().filter(task -> story.getId().equals(task.getStoryId())).toList();
    return !storyTasks.isEmpty()
        && storyTasks.stream().allMatch(task -> "Done".equals(task.getStatus()));
  }

  public static long workingDays(LocalDate start, LocalDate end) {
    if (end.isBefore(start)) return 0;
    long days = ChronoUnit.DAYS.between(start, end) + 1;
    long count = days / 7 * 5;
    for (int offset = 0; offset < days % 7; offset++) {
      DayOfWeek weekday = start.plusDays(offset).getDayOfWeek();
      if (weekday != DayOfWeek.SATURDAY && weekday != DayOfWeek.SUNDAY) count++;
    }
    return count;
  }

  public static List<Map<String, Object>> burndownFromHistory(
      List<Task> tasks, List<TaskStatusHistory> history, LocalDate start, LocalDate end) {
    return burndownFromHistory(tasks, history, start, end, null, null);
  }

  private static List<Map<String, Object>> burndownFromHistory(
      List<Task> tasks,
      List<TaskStatusHistory> history,
      LocalDate start,
      LocalDate end,
      Long sprintId,
      Instant closure) {
    if (end.isBefore(start)) return List.of();
    long days = ChronoUnit.DAYS.between(start, end) + 1;
    if (days > 180) start = end.minusDays(179);
    LocalDate chartStart = start;
    return java.util.stream.LongStream.range(0, ChronoUnit.DAYS.between(chartStart, end) + 1)
        .mapToObj(
            offset -> {
              LocalDate day = chartStart.plusDays(offset);
              Instant cutoff = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
              int remaining =
                  tasks.stream()
                      .filter(
                          task ->
                              task.getCreatedAt() != null && !task.getCreatedAt().isAfter(cutoff))
                      .mapToInt(
                          task ->
                              history.stream()
                                  .filter(
                                      event ->
                                          task.getId().equals(event.getTaskId())
                                              && event.getChangedAt() != null
                                              && event.getChangedAt().isBefore(cutoff)
                                              && (closure == null
                                                  || !event.getChangedAt().isAfter(closure)))
                                  .max(EVENT_ORDER)
                                  .filter(
                                      event ->
                                          sprintId == null || sprintId.equals(event.getSprintId()))
                                  .filter(event -> !"Done".equals(event.getStatus()))
                                  .map(TaskStatusHistory::getStoryPoints)
                                  .orElse(0))
                      .sum();
              return Map.<String, Object>of("date", day.toString(), "remainingPoints", remaining);
            })
        .toList();
  }

  private Map<String, Object> health(List<Task> tasks, long blocked, long wip, Double reviewHours) {
    boolean reviewBottleneck = reviewHours != null && reviewHours >= 24;
    boolean highWip = wip > Math.max(3, tasks.size() / 2);
    String state =
        blocked > 0 || reviewBottleneck ? "At risk" : (highWip ? "Watch WIP" : "Healthy");
    String explanation =
        blocked > 0
            ? blocked + " blocked task(s) need attention."
            : reviewBottleneck
                ? "Tasks have waited 24 hours or more in Review."
                : highWip
                    ? "Too much work is active at once."
                    : "No current delivery risk was detected.";
    return Map.of(
        "status",
        state,
        "explanation",
        explanation,
        "blockedTasks",
        blocked,
        "workInProgress",
        wip);
  }

  private Map<String, Object> sprintSnapshot(
      Sprint sprint, Map<Long, List<TaskStatusHistory>> historyByTask) {
    // Historical delivery uses the state at closure, not later edits or sprint moves.
    List<TaskStatusHistory> done =
        historyByTask.values().stream()
            .map(
                events ->
                    events.stream()
                        .filter(
                            event ->
                                event.getChangedAt() != null
                                    && !event.getChangedAt().isAfter(sprint.getCompletedAt()))
                        .max(
                            java.util.Comparator.comparing(TaskStatusHistory::getChangedAt)
                                .thenComparing(
                                    event ->
                                        event.getId() == null ? Long.MIN_VALUE : event.getId()))
                        .orElse(null))
            .filter(java.util.Objects::nonNull)
            .filter(
                event ->
                    "Done".equals(event.getStatus()) && sprint.getId().equals(event.getSprintId()))
            .toList();
    return Map.of(
        "id",
        sprint.getId(),
        "name",
        sprint.getName(),
        "completedAt",
        sprint.getCompletedAt(),
        "plannedStoryPoints",
        sprint.getTotalStoryPoints(),
        "actualStoryPoints",
        done.stream().mapToInt(TaskStatusHistory::getStoryPoints).sum(),
        "completedTasks",
        done.size());
  }

  private static final java.util.Comparator<TaskStatusHistory> EVENT_ORDER =
      java.util.Comparator.comparing(TaskStatusHistory::getChangedAt)
          .thenComparing(event -> event.getId() == null ? Long.MIN_VALUE : event.getId());

  private List<TaskStatusHistory> statusTransitions(List<TaskStatusHistory> events) {
    List<TaskStatusHistory> transitions = new java.util.ArrayList<>();
    for (TaskStatusHistory event :
        events.stream().filter(item -> item.getChangedAt() != null).sorted(EVENT_ORDER).toList()) {
      if (transitions.isEmpty()
          || !java.util.Objects.equals(
              transitions.get(transitions.size() - 1).getStatus(), event.getStatus()))
        transitions.add(event);
    }
    return transitions;
  }

  private Double reviewWaitingHours(
      List<Task> tasks, Map<Long, List<TaskStatusHistory>> transitions) {
    Instant now = Instant.now();
    List<Long> values =
        tasks.stream()
            .filter(task -> "Review".equals(task.getStatus()))
            .map(
                task ->
                    transitions.getOrDefault(task.getId(), List.of()).stream()
                        .max(EVENT_ORDER)
                        .filter(event -> "Review".equals(event.getStatus()))
                        .map(event -> validMinutes(event.getChangedAt(), now))
                        .orElse(null))
            .filter(java.util.Objects::nonNull)
            .toList();
    return averageHours(values);
  }
}
