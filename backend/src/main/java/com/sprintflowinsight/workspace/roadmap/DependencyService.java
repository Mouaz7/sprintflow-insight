package com.sprintflowinsight.workspace.roadmap;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.common.error.ErrorCode;
import com.sprintflowinsight.workspace.activity.ActivityService;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectMutationService;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.DependencyView;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.EstimateView;
import com.sprintflowinsight.workspace.roadmap.schedule.DependencyGraph;
import com.sprintflowinsight.workspace.roadmap.schedule.Edge;
import com.sprintflowinsight.workspace.roadmap.schedule.ThreePointEstimate;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskRepository;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task dependencies and three-point estimates. Every change runs under the project row lock, so two
 * concurrent requests cannot each add half of a cycle.
 */
@Service
public class DependencyService {
  /** Bounds the graph a single cycle check or roadmap read has to traverse. */
  static final int MAX_DEPENDENCIES_PER_PROJECT = 5000;

  private final TaskDependencyRepository dependencies;
  private final TaskEstimateRepository estimates;
  private final TaskRepository tasks;
  private final ProjectAccessService access;
  private final ProjectMutationService mutations;
  private final ActivityService activity;

  DependencyService(
      TaskDependencyRepository dependencies,
      TaskEstimateRepository estimates,
      TaskRepository tasks,
      ProjectAccessService access,
      ProjectMutationService mutations,
      ActivityService activity) {
    this.dependencies = dependencies;
    this.estimates = estimates;
    this.tasks = tasks;
    this.access = access;
    this.mutations = mutations;
    this.activity = activity;
  }

  @Transactional
  public DependencyView add(Long projectId, DependencyRequest request, HttpSession session) {
    access.write(projectId, session);
    mutations.lock(projectId);
    Task blocker = liveTask(projectId, request.blockerTaskId());
    Task blocked = liveTask(projectId, request.blockedTaskId());
    if (blocker.getId().equals(blocked.getId()))
      throw ApiException.badRequest("A task cannot depend on itself.");
    if (dependencies.existsByBlockerTaskIdAndBlockedTaskId(blocker.getId(), blocked.getId()))
      throw ApiException.conflict(blocker.getKey() + " already blocks " + blocked.getKey() + ".");
    List<TaskDependency> existing = dependencies.findByProjectIdOrderByIdAsc(projectId);
    if (existing.size() >= MAX_DEPENDENCIES_PER_PROJECT)
      throw ApiException.conflict(
          "A project can have at most " + MAX_DEPENDENCIES_PER_PROJECT + " dependencies.");
    // Trashed tasks keep their edges, so a later restore can never bring back a cycle.
    DependencyGraph graph =
        DependencyGraph.of(
            List.of(),
            existing.stream()
                .map(d -> new Edge(d.getBlockerTaskId(), d.getBlockedTaskId()))
                .toList());
    var cycle = graph.cycleIfAdded(blocker.getId(), blocked.getId());
    if (cycle.isPresent()) throw cycleProblem(cycle.get());
    TaskDependency saved =
        dependencies.saveAndFlush(
            new TaskDependency(
                projectId, blocker.getId(), blocked.getId(), access.userId(session), Instant.now()));
    activity.record(
        projectId,
        "DEPENDENCY_ADDED",
        blocker.getKey() + " now blocks " + blocked.getKey() + ".",
        access.email(session));
    return new DependencyView(
        saved.getId(),
        RoadmapAssembler.ref(blocker),
        RoadmapAssembler.ref(blocked),
        !RoadmapAssembler.done(blocker));
  }

  @Transactional
  public void remove(Long projectId, Long dependencyId, HttpSession session) {
    access.write(projectId, session);
    mutations.lock(projectId);
    TaskDependency dependency =
        dependencies
            .findByIdAndProjectId(dependencyId, projectId)
            .orElseThrow(() -> ApiException.notFound("Dependency not found."));
    Map<Long, Task> ends =
        tasks.findAllById(List.of(dependency.getBlockerTaskId(), dependency.getBlockedTaskId()))
            .stream()
            .collect(Collectors.toMap(Task::getId, Function.identity()));
    dependencies.delete(dependency);
    dependencies.flush();
    activity.record(
        projectId,
        "DEPENDENCY_REMOVED",
        key(ends.get(dependency.getBlockerTaskId()))
            + " no longer blocks "
            + key(ends.get(dependency.getBlockedTaskId()))
            + ".",
        access.email(session));
  }

  @Transactional
  public EstimateView estimate(
      Long projectId, Long taskId, EstimateRequest request, HttpSession session) {
    access.write(projectId, session);
    mutations.lock(projectId);
    Task task = liveTask(projectId, taskId);
    TaskEstimate row =
        estimates.findById(task.getId()).orElseGet(() -> new TaskEstimate(task.getId(), projectId));
    row.set(request.optimistic(), request.mostLikely(), request.pessimistic(), Instant.now());
    return view(estimates.saveAndFlush(row));
  }

  @Transactional
  public void clearEstimate(Long projectId, Long taskId, HttpSession session) {
    access.write(projectId, session);
    mutations.lock(projectId);
    liveTask(projectId, taskId);
    estimates.findById(taskId).ifPresent(estimates::delete);
  }

  static EstimateView view(TaskEstimate row) {
    ThreePointEstimate estimate = row.toEstimate();
    return new EstimateView(
        row.getTaskId(),
        estimate.optimistic(),
        estimate.mostLikely(),
        estimate.pessimistic(),
        estimate.mean(),
        estimate.standardDeviation());
  }

  private ApiException cycleProblem(List<Long> cycle) {
    Map<Long, Task> byId =
        tasks.findAllById(cycle).stream().collect(Collectors.toMap(Task::getId, Function.identity()));
    String path = cycle.stream().map(id -> key(byId.get(id))).collect(Collectors.joining(" → "));
    return new ApiException(
            ErrorCode.CONFLICT, "This dependency would create a cycle: " + path + ".")
        .with("cycle", cycle);
  }

  private static String key(Task task) {
    return task == null ? "a deleted task" : task.getKey();
  }

  private Task liveTask(Long projectId, Long taskId) {
    return tasks
        .findById(taskId)
        .filter(t -> t.getTrashedAt() == null && projectId.equals(t.getProjectId()))
        .orElseThrow(() -> ApiException.notFound("Task not found."));
  }
}
