package com.sprintflowinsight.reporting;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.common.error.ErrorCode;
import com.sprintflowinsight.forecast.ForecastAnswers;
import com.sprintflowinsight.reporting.ForecastResponse.TargetSource;
import com.sprintflowinsight.reporting.ForecastResponse.WorkScope;
import com.sprintflowinsight.reporting.ThroughputDerivation.Derived;
import com.sprintflowinsight.reporting.ThroughputDerivation.SprintRecord;
import com.sprintflowinsight.reporting.ThroughputDerivation.StatusEvent;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.roadmap.EpicService;
import com.sprintflowinsight.workspace.sprint.Sprint;
import com.sprintflowinsight.workspace.sprint.SprintRepository;
import com.sprintflowinsight.workspace.story.UserStory;
import com.sprintflowinsight.workspace.story.UserStoryRepository;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** B6: project-scoped snapshot reads and cached, reproducible bootstrap forecasts. */
@Service
public class ForecastService {
  static final int CACHE_SIZE = 256;
  private final ProjectAccessService access;
  private final TaskRepository tasks;
  private final TaskStatusHistoryRepository history;
  private final SprintRepository sprints;
  private final UserStoryRepository stories;
  private final EpicService epics;
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
      SprintRepository sprints,
      UserStoryRepository stories,
      EpicService epics) {
    this(access, tasks, history, sprints, stories, epics, Clock.systemUTC());
  }

  ForecastService(
      ProjectAccessService access,
      TaskRepository tasks,
      TaskStatusHistoryRepository history,
      SprintRepository sprints,
      UserStoryRepository stories,
      EpicService epics,
      Clock clock) {
    this.access = access;
    this.tasks = tasks;
    this.history = history;
    this.sprints = sprints;
    this.stories = stories;
    this.epics = epics;
    this.clock = clock;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ForecastResponse forecast(Long projectId, ForecastRequest request, HttpSession session) {
    access.read(projectId, session);
    var inputs = inputs(projectId, request, session);
    ForecastResponse cached = cache.get(inputs);
    if (cached != null) return cached;
    var response = ForecastCalculator.calculate(inputs);
    cache.put(inputs, response);
    return response;
  }

  private ForecastCalculator.Inputs inputs(
      Long projectId, ForecastRequest request, HttpSession session) {
    Instant now = clock.instant();
    LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
    validateTarget(request, today);
    ForecastBasis basis = request.basisOrDefault();
    String epicTitle = null;
    if (request.epicId() != null) {
      epicTitle =
          epics.list(projectId, session).stream()
              .filter(epic -> epic.id().equals(request.epicId()))
              .findFirst()
              .orElseThrow(() -> ApiException.notFound("Epic not found in this project."))
              .title();
    }
    List<Task> live = tasks.findByProjectId(projectId);
    Set<Long> liveIds = live.stream().map(Task::getId).collect(Collectors.toSet());
    List<StatusEvent> events =
        history.findByProjectIdOrderByChangedAtAscIdAsc(projectId).stream()
            .filter(
                row ->
                    row.getTaskId() != null
                        && row.getChangedAt() != null
                        && row.getStatus() != null)
            .filter(row -> !row.getChangedAt().isAfter(now))
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
            Instant observed =
                events.stream()
                    .filter(row -> liveIds.contains(row.taskId()))
                    .map(StatusEvent::changedAt)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            yield ThroughputDerivation.weekly(
                ThroughputDerivation.completions(done, events), today, observed);
          }
          case VELOCITY ->
              ThroughputDerivation.velocity(
                  projectSprints.stream().map(ForecastService::sprint).toList(), events, now);
        };
    List<UserStory> scopeStories =
        stories.findByProjectId(projectId).stream()
            .filter(story -> request.epicId() == null || request.epicId().equals(story.getEpicId()))
            .toList();
    Set<Long> storyIds = scopeStories.stream().map(UserStory::getId).collect(Collectors.toSet());
    List<Task> scoped =
        live.stream()
            .filter(task -> request.epicId() == null || storyIds.contains(task.getStoryId()))
            .toList();
    List<Task> open =
        scoped.stream()
            .filter(task -> !ThroughputDerivation.DONE.equals(task.getStatus()))
            .toList();
    Set<Long> plannedStories = scoped.stream().map(Task::getStoryId).collect(Collectors.toSet());
    Set<Long> observedTasks = events.stream().map(StatusEvent::taskId).collect(Collectors.toSet());
    var scope =
        new WorkScope(
            request.epicId(),
            epicTitle,
            open.size(),
            points(open),
            (int)
                scopeStories.stream()
                    .filter(story -> !plannedStories.contains(story.getId()))
                    .count(),
            (int) open.stream().filter(task -> task.getStoryPoints() == 0).count(),
            (int) live.stream().filter(task -> !observedTasks.contains(task.getId())).count());
    int remaining = request.remaining() != null ? request.remaining() : work(open, basis);
    var target =
        target(
            request, today, basis, open, projectSprints, remaining, derived.history().periodDays());
    return new ForecastCalculator.Inputs(basis, derived, today, remaining, target, scope);
  }

  private static void validateTarget(ForecastRequest request, LocalDate today) {
    if (request.targetItems() != null && request.targetDate() == null)
      throw new ApiException(
          ErrorCode.MISSING_PARAMETER, "targetDate is required when targetItems is given.");
    LocalDate date = request.targetDate();
    if (date != null
        && (date.isBefore(today) || date.isAfter(today.plusDays(ForecastAnswers.DEFAULT_MAX_DAYS))))
      throw new ApiException(
          ErrorCode.INVALID_PARAMETER,
          "targetDate must be between today and five years ahead (UTC).");
  }

  private static ForecastCalculator.Target target(
      ForecastRequest request,
      LocalDate today,
      ForecastBasis basis,
      List<Task> open,
      List<Sprint> sprints,
      int remaining,
      int periodDays) {
    if (request.targetDate() != null)
      return new ForecastCalculator.Target(
          request.targetDate(),
          request.targetItems() == null ? remaining : request.targetItems(),
          TargetSource.REQUEST);
    return sprints.stream()
        .filter(sprint -> "Active".equals(sprint.getStatus()) && sprint.getEndDate() != null)
        .sorted(Comparator.comparing(Sprint::getEndDate).thenComparing(Sprint::getId))
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
        .orElseGet(
            () ->
                new ForecastCalculator.Target(
                    today.plusDays(Math.min(4L * periodDays, ForecastAnswers.DEFAULT_MAX_DAYS)),
                    remaining,
                    TargetSource.DEFAULT));
  }

  private static long points(List<Task> open) {
    return open.stream().mapToLong(task -> Math.max(0, task.getStoryPoints())).sum();
  }

  private static int work(List<Task> open, ForecastBasis basis) {
    long quantity = basis == ForecastBasis.VELOCITY ? points(open) : open.size();
    if (quantity > ForecastRequest.MAX_WORK)
      throw new ApiException(
          ErrorCode.INVALID_PARAMETER,
          "More than 10,000 units remain. Choose an epic or enter a smaller explicit remaining"
              + " amount; the forecast never silently truncates work.");
    return (int) quantity;
  }

  private static StatusEvent event(TaskStatusHistory row) {
    return new StatusEvent(
        row.getTaskId(),
        row.getId() == null ? Long.MIN_VALUE : row.getId(),
        row.getStatus(),
        row.getSprintId(),
        row.getChangedAt(),
        row.getStoryPoints());
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
