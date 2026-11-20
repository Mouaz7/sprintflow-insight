package com.sprintflowinsight.reporting;

import com.sprintflowinsight.VersionPrecondition;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.common.error.ErrorCode;
import com.sprintflowinsight.flow.FlowAnalysis;
import com.sprintflowinsight.flow.FlowStatus;
import com.sprintflowinsight.flow.LittlesLaw;
import com.sprintflowinsight.flow.TaskTimeline;
import com.sprintflowinsight.flow.Transition;
import com.sprintflowinsight.flow.XmrChart;
import com.sprintflowinsight.workspace.activity.ActivityService;
import com.sprintflowinsight.workspace.project.AccessContext.Need;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectMutationService;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskRepository;
import com.sprintflowinsight.workspace.task.TaskStatusHistory;
import com.sprintflowinsight.workspace.task.TaskStatusHistoryRepository;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flow analytics (B5) and WIP limits. Reads cost a fixed number of queries per request: access,
 * tasks, the project's status history and its limits, whatever the number of tasks.
 */
@Service
public class FlowService {
  private final ProjectAccessService access;
  private final TaskRepository tasks;
  private final TaskStatusHistoryRepository history;
  private final ProjectWipLimitRepository limits;
  private final Clock clock;
  private final ProjectMutationService mutations;
  private final ProjectWipSettingsRepository settings;
  private final ActivityService activity;
  private final com.sprintflowinsight.common.audit.AuditService audit;

  @Autowired
  public FlowService(
      ProjectAccessService access,
      TaskRepository tasks,
      TaskStatusHistoryRepository history,
      ProjectWipLimitRepository limits,
      ProjectMutationService mutations,
      ProjectWipSettingsRepository settings,
      ActivityService activity,
      com.sprintflowinsight.common.audit.AuditService audit) {
    this(access, tasks, history, limits, mutations, settings, activity, audit, Clock.systemUTC());
  }

  FlowService(
      ProjectAccessService access,
      TaskRepository tasks,
      TaskStatusHistoryRepository history,
      ProjectWipLimitRepository limits,
      ProjectMutationService mutations,
      ProjectWipSettingsRepository settings,
      ActivityService activity,
      com.sprintflowinsight.common.audit.AuditService audit,
      Clock clock) {
    this.access = access;
    this.tasks = tasks;
    this.history = history;
    this.limits = limits;
    this.clock = clock;
    this.mutations = mutations;
    this.settings = settings;
    this.activity = activity;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public FlowResponse flow(Long projectId, FlowQuery query, HttpSession session) {
    access.require(projectId, session, Need.READ);
    int days = query.windowDays();
    Instant now = clock.instant();
    LocalDate end = LocalDate.ofInstant(now, ZoneOffset.UTC);
    LocalDate start = end.minusDays(days - 1L);

    // Trashed tasks are left out, as on the board.
    List<Task> visible = tasks.findByProjectId(projectId);
    Map<Long, Task> byId =
        visible.stream().collect(Collectors.toMap(Task::getId, Function.identity()));
    Map<Long, List<Transition>> transitions = new HashMap<>();
    for (TaskStatusHistory row : history.findByProjectIdOrderByChangedAtAscIdAsc(projectId)) {
      if (row.getTaskId() == null
          || row.getStatus() == null
          || row.getChangedAt() == null
          || !byId.containsKey(row.getTaskId())) continue;
      transitions
          .computeIfAbsent(row.getTaskId(), id -> new ArrayList<>())
          .add(new Transition(row.getStatus(), row.getChangedAt()));
    }
    List<FlowAnalysis.Item> items =
        visible.stream()
            .map(
                task ->
                    new FlowAnalysis.Item(
                        task.getId(),
                        TaskTimeline.of(
                            task.getCreatedAt(), transitions.getOrDefault(task.getId(), List.of())),
                        task.getStatus()))
            .toList();
    FlowAnalysis.Report report = FlowAnalysis.analyse(items, start, end, now, limitMap(projectId));
    return response(days, now, report, byId);
  }

  @Transactional(readOnly = true)
  public WipSettingsResponse limits(Long projectId, HttpSession session) {
    access.require(projectId, session, Need.READ);
    return new WipSettingsResponse(revision(projectId), limitResponses(limitMap(projectId)));
  }

  @Transactional
  public WipSettingsResponse replaceLimits(
      Long projectId, WipLimitsRequest request, String ifMatch, HttpSession session) {
    access.require(projectId, session, Need.MANAGE);
    mutations.lock(projectId);
    access.forget(projectId);
    var caller = access.require(projectId, session, Need.MANAGE);
    long version = revision(projectId);
    VersionPrecondition.require(ifMatch, version);
    Set<String> seen = new HashSet<>();
    for (WipLimitsRequest.Entry entry : request.limits())
      if (!seen.add(entry.status()))
        throw new ApiException(
            ErrorCode.VALIDATION_FAILED, "Each column can have only one WIP limit.");
    limits.deleteByProjectId(projectId);
    limits.saveAll(
        request.limits().stream()
            .map(entry -> new ProjectWipLimit(projectId, entry.status(), entry.limit()))
            .toList());
    Map<String, Integer> saved = new LinkedHashMap<>();
    request.limits().forEach(entry -> saved.put(entry.status(), entry.limit()));
    settings.saveAndFlush(new ProjectWipSettings(projectId, version + 1));
    activity.record(
        projectId, "WIP_LIMITS_UPDATED", "Updated the board's WIP limits.", access.email(session));
    audit.record(
        projectId,
        caller.userId(),
        caller.role().label(),
        "WIP_LIMITS_UPDATED",
        "project",
        projectId,
        Map.of("columns", saved.size()));
    return new WipSettingsResponse(version + 1, limitResponses(saved));
  }

  private long revision(Long projectId) {
    return settings.findById(projectId).map(ProjectWipSettings::getVersion).orElse(0L);
  }

  private Map<String, Integer> limitMap(Long projectId) {
    Map<String, Integer> result = new HashMap<>();
    for (ProjectWipLimit limit : limits.findByProjectId(projectId))
      result.put(limit.getStatus(), limit.getLimit());
    return result;
  }

  private static List<WipLimitResponse> limitResponses(Map<String, Integer> limits) {
    return FlowStatus.LIMITABLE.stream()
        .map(status -> new WipLimitResponse(status, limits.get(status)))
        .toList();
  }

  private static FlowResponse response(
      int days, Instant now, FlowAnalysis.Report report, Map<Long, Task> tasks) {
    List<FlowResponse.CumulativeFlowDay> cfd =
        report.cumulativeFlow().stream()
            .map(
                day ->
                    new FlowResponse.CumulativeFlowDay(
                        day.date(),
                        day.counts().get(FlowStatus.TO_DO),
                        day.counts().get(FlowStatus.IN_PROGRESS),
                        day.counts().get(FlowStatus.REVIEW),
                        day.counts().get(FlowStatus.DONE)))
            .toList();
    List<FlowResponse.AgingTask> aging =
        report.aging().stream()
            .map(
                item -> {
                  Task task = tasks.get(item.taskId());
                  return new FlowResponse.AgingTask(
                      item.taskId(),
                      task.getKey(),
                      task.getTitle(),
                      item.status(),
                      round(item.ageHours()),
                      round(item.statusAgeHours()),
                      item.band().name());
                })
            .toList();
    List<FlowResponse.Column> columns =
        report.columns().stream()
            .map(
                column ->
                    new FlowResponse.Column(
                        column.status(),
                        column.currentWip(),
                        round(column.averageWip()),
                        column.limit(),
                        column.overLimit(),
                        column.bottleneck() == null ? null : column.bottleneck().name()))
            .toList();
    LittlesLaw law = report.littlesLaw();
    return new FlowResponse(
        days,
        report.start(),
        report.end(),
        now,
        cfd,
        distribution(report.cycleTime()),
        distribution(report.leadTime()),
        report.completions().size(),
        round(report.throughputPerDay()),
        report.completions().stream()
            .map(
                item ->
                    new FlowResponse.Completion(
                        item.taskId(),
                        tasks.get(item.taskId()).getKey(),
                        item.doneAt(),
                        round(item.cycleTimeHours()),
                        round(item.leadTimeHours())))
            .toList(),
        aging,
        columns,
        controlChart(report, tasks),
        new FlowResponse.LittlesLawCheck(
            law.status().name(),
            round(law.averageWip()),
            round(law.throughputPerDay()),
            round(law.averageCycleTimeDays()),
            round(law.expectedWip()),
            law.ratio() == null ? null : round(law.ratio()),
            LittlesLaw.DEFAULT_TOLERANCE),
        report.tasksWithoutHistory());
  }

  private static FlowResponse.ControlChart controlChart(
      FlowAnalysis.Report report, Map<Long, Task> tasks) {
    if (report.controlChart().isEmpty())
      return new FlowResponse.ControlChart(
          false, XmrChart.MIN_POINTS, null, null, null, null, null, 0, List.of());
    XmrChart chart = report.controlChart().get();
    List<FlowAnalysis.CompletedItem> measured =
        report.completions().stream().filter(item -> item.cycleTimeHours() != null).toList();
    List<FlowResponse.ControlPoint> points = new ArrayList<>(measured.size());
    for (int i = 0; i < measured.size(); i++) {
      FlowAnalysis.CompletedItem item = measured.get(i);
      XmrChart.Point point = chart.points().get(i);
      points.add(
          new FlowResponse.ControlPoint(
              item.taskId(),
              tasks.get(item.taskId()).getKey(),
              item.doneAt(),
              round(point.value()),
              point.movingRange() == null ? null : round(point.movingRange()),
              point.signals().stream().map(Enum::name).toList()));
    }
    return new FlowResponse.ControlChart(
        true,
        XmrChart.MIN_POINTS,
        round(chart.mean()),
        round(chart.upperLimit()),
        round(Math.max(0, chart.lowerLimit())),
        round(chart.movingRangeMean()),
        round(chart.upperRangeLimit()),
        (int) chart.signalCount(),
        points);
  }

  private static FlowResponse.Distribution distribution(FlowAnalysis.Distribution value) {
    return new FlowResponse.Distribution(
        value.count(),
        round(value.mean()),
        round(value.p50()),
        round(value.p85()),
        round(value.p95()));
  }

  private static Double round(Double value) {
    return value == null ? null : round(value.doubleValue());
  }

  private static double round(double value) {
    return Math.round(value * 100d) / 100d;
  }
}
