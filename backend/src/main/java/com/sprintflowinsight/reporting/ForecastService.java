package com.sprintflowinsight.reporting;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.common.error.ErrorCode;
import com.sprintflowinsight.forecast.ForecastAnswers;
import com.sprintflowinsight.reporting.ForecastResponse.TargetSource;
import com.sprintflowinsight.reporting.ThroughputDerivation.Derived;
import com.sprintflowinsight.reporting.ThroughputDerivation.SprintRecord;
import com.sprintflowinsight.reporting.ThroughputDerivation.StatusEvent;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.sprint.Sprint;
import com.sprintflowinsight.workspace.sprint.SprintRepository;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskRepository;
import com.sprintflowinsight.workspace.task.TaskStatusHistory;
import com.sprintflowinsight.workspace.task.TaskStatusHistoryRepository;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a project's throughput or velocity history from persisted workflow snapshots and answers
 * the forecast questions with the model chosen by backtesting. Uses a fixed number of queries
 * (access, tasks, status history, sprints) regardless of project size.
 */
@Service
public class ForecastService {
  /** Forecasts are pure functions of their inputs, so equal inputs reuse the earlier result. */
  static final int CACHE_SIZE = 256;

  private static final String ACTIVE = "Active";

  private final ProjectAccessService access;
  private final TaskRepository tasks;
  private final TaskStatusHistoryRepository history;
  private final SprintRepository sprints;
  private final Clock clock;
  private final Map<ForecastCalculator.Inputs, ForecastResponse> cache =
      Collections.synchronizedMap(
          new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(
                Map.Entry<ForecastCalculator.Inputs, ForecastResponse> eldest) {
              return size() > CACHE_SIZE;
            }
          });

  @Autowired
  public ForecastService(
      ProjectAccessService access,
      TaskRepository tasks,
      TaskStatusHistoryRepository history,
      SprintRepository sprints) {
    this(access, tasks, history, sprints, Clock.systemUTC());
  }

  ForecastService(
      ProjectAccessService access,
      TaskRepository tasks,
      TaskStatusHistoryRepository history,
      SprintRepository sprints,
      Clock clock) {
    this.access = access;
    this.tasks = tasks;
    this.history = history;
    this.sprints = sprints;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public ForecastResponse forecast(Long projectId, ForecastRequest request, HttpSession session) {
    access.read(projectId, session);
    ForecastCalculator.Inputs inputs = inputs(projectId, request);
    ForecastResponse cached = cache.get(inputs);
    if (cached != null) return cached;
    ForecastResponse response = ForecastCalculator.calculate(inputs);
    cache.put(inputs, response);
    return response;
  }

  private ForecastCalculator.Inputs inputs(Long projectId, ForecastRequest request) {
    Instant now = clock.instant();
    LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
    ForecastBasis basis = request.basisOrDefault();
    validateTarget(request, today);

    List<Task> live = tasks.findByProjectId(projectId);
    List<StatusEvent> events =
        history.findByProjectIdOrderByChangedAtDesc(projectId).stream()
            .filter(event -> event.getTaskId() != null && event.getChangedAt() != null)
            .map(ForecastService::event)
            .toList();
    List<Sprint> projectSprints = sprints.findByProjectId(projectId);

    Derived derived =
        switch (basis) {
          case THROUGHPUT -> {
            Set<Long> done =
                live.stream()
                    .filter(task -> ThroughputDerivation.DONE.equals(task.getStatus()))
                    .map(Task::getId)
                    .collect(Collectors.toSet());
            yield ThroughputDerivation.weekly(
                ThroughputDerivation.completions(done, events), today);
          }
          case VELOCITY ->
              ThroughputDerivation.velocity(
                  projectSprints.stream().map(ForecastService::sprint).toList(), events, now);
        };

    List<Task> open =
        live.stream().filter(task -> !ThroughputDerivation.DONE.equals(task.getStatus())).toList();
    int remaining = request.remaining() != null ? request.remaining() : work(open, basis);
    ForecastCalculator.Target target =
        target(request, today, basis, open, projectSprints, remaining);
    return new ForecastCalculator.Inputs(basis, derived, today, remaining, target);
  }

  private static void validateTarget(ForecastRequest request, LocalDate today) {
    if (request.targetItems() != null && request.targetDate() == null) {
      throw new ApiException(
          ErrorCode.MISSING_PARAMETER, "targetDate is required when targetItems is given.");
    }
    LocalDate date = request.targetDate();
    if (date != null
        && (date.isBefore(today)
            || date.isAfter(today.plusDays(ForecastAnswers.DEFAULT_MAX_DAYS)))) {
      throw new ApiException(
          ErrorCode.INVALID_PARAMETER,
          "targetDate must be between today and five years ahead (UTC).");
    }
  }

  /** The explicit target, or by default the active sprint's unfinished work by its end date. */
  private static ForecastCalculator.Target target(
      ForecastRequest request,
      LocalDate today,
      ForecastBasis basis,
      List<Task> open,
      List<Sprint> projectSprints,
      int remaining) {
    if (request.targetDate() != null) {
      int items = request.targetItems() != null ? request.targetItems() : remaining;
      return new ForecastCalculator.Target(request.targetDate(), items, TargetSource.REQUEST);
    }
    return projectSprints.stream()
        .filter(sprint -> ACTIVE.equals(sprint.getStatus()))
        .filter(sprint -> sprint.getEndDate() != null && !sprint.getEndDate().isBefore(today))
        .findFirst()
        .map(
            sprint ->
                new ForecastCalculator.Target(
                    sprint.getEndDate(),
                    work(
                        open.stream()
                            .filter(task -> sprint.getId().equals(task.getSprintId()))
                            .toList(),
                        basis),
                    TargetSource.ACTIVE_SPRINT))
        .orElse(null);
  }

  private static int work(List<Task> open, ForecastBasis basis) {
    long total =
        basis == ForecastBasis.VELOCITY
            ? open.stream().mapToLong(task -> Math.max(0, task.getStoryPoints())).sum()
            : open.size();
    return (int) Math.min(total, ForecastRequest.MAX_WORK);
  }

  private static StatusEvent event(TaskStatusHistory event) {
    return new StatusEvent(
        event.getTaskId(),
        event.getId() == null ? Long.MIN_VALUE : event.getId(),
        event.getStatus(),
        event.getSprintId(),
        event.getChangedAt(),
        event.getStoryPoints());
  }

  private static SprintRecord sprint(Sprint sprint) {
    return new SprintRecord(
        sprint.getId(),
        sprint.getName(),
        sprint.getStatus(),
        sprint.getStartDate(),
        sprint.getEndDate(),
        sprint.getCreatedAt(),
        sprint.getCompletedAt());
  }
}
