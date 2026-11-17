package com.sprintflowinsight.workspace.roadmap;

import com.sprintflowinsight.workspace.roadmap.RoadmapViews.DependencyOverview;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.DependencyView;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.EpicLink;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.EpicView;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.ExternalBlock;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.Forecast;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.Roadmap;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.RoadmapEpic;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.RoadmapSprint;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.TaskRef;
import com.sprintflowinsight.workspace.roadmap.schedule.CriticalPath;
import com.sprintflowinsight.workspace.roadmap.schedule.DependencyGraph;
import com.sprintflowinsight.workspace.roadmap.schedule.Edge;
import com.sprintflowinsight.workspace.roadmap.schedule.PertForecast;
import com.sprintflowinsight.workspace.roadmap.schedule.ThreePointEstimate;
import com.sprintflowinsight.workspace.sprint.Sprint;
import com.sprintflowinsight.workspace.story.UserStory;
import com.sprintflowinsight.workspace.task.Task;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds roadmap and dependency views from already loaded rows. Pure and deterministic, so the
 * services load each table once and this class is tested without a database.
 */
final class RoadmapAssembler {
  static final String DONE = "Done";
  static final String ACTIVE = "Active";

  private final Map<Long, Task> tasks;
  private final List<TaskDependency> dependencies;
  private final Map<Long, ThreePointEstimate> estimates;

  RoadmapAssembler(
      Collection<Task> liveTasks,
      Collection<TaskDependency> allDependencies,
      Collection<TaskEstimate> estimateRows) {
    tasks =
        liveTasks.stream()
            .collect(Collectors.toMap(Task::getId, Function.identity(), (a, b) -> a, TreeMap::new));
    dependencies =
        allDependencies.stream()
            .filter(
                d -> tasks.containsKey(d.getBlockerTaskId()) && tasks.containsKey(d.getBlockedTaskId()))
            .sorted(Comparator.comparing(TaskDependency::getId))
            .toList();
    estimates = new TreeMap<>();
    for (TaskEstimate row : estimateRows)
      if (tasks.containsKey(row.getTaskId())) estimates.put(row.getTaskId(), row.toEstimate());
  }

  static boolean done(Task task) {
    return DONE.equals(task.getStatus());
  }

  static TaskRef ref(Task task) {
    return new TaskRef(task.getId(), task.getKey(), task.getTitle(), task.getStatus(), task.getSprintId());
  }

  DependencyOverview overview(Collection<Sprint> sprints) {
    Long activeSprint =
        sprints.stream()
            .filter(s -> ACTIVE.equals(s.getStatus()))
            .map(Sprint::getId)
            .findFirst()
            .orElse(null);
    List<DependencyView> views = new ArrayList<>();
    Set<Long> blocked = new TreeSet<>();
    List<ExternalBlock> external = new ArrayList<>();
    for (TaskDependency dependency : dependencies) {
      Task blocker = tasks.get(dependency.getBlockerTaskId());
      Task target = tasks.get(dependency.getBlockedTaskId());
      boolean open = !done(blocker);
      views.add(new DependencyView(dependency.getId(), ref(blocker), ref(target), open));
      if (open && !done(target)) {
        blocked.add(target.getId());
        if (activeSprint != null
            && activeSprint.equals(target.getSprintId())
            && !activeSprint.equals(blocker.getSprintId()))
          external.add(new ExternalBlock(ref(target), ref(blocker)));
      }
    }
    return new DependencyOverview(List.copyOf(views), List.copyOf(blocked), List.copyOf(external));
  }

  /** Edges between unfinished tasks; finished blockers no longer constrain the schedule. */
  DependencyGraph openGraph() {
    List<Long> open = tasks.values().stream().filter(t -> !done(t)).map(Task::getId).toList();
    List<Edge> edges =
        dependencies.stream()
            .filter(
                d -> !done(tasks.get(d.getBlockerTaskId())) && !done(tasks.get(d.getBlockedTaskId())))
            .map(d -> new Edge(d.getBlockerTaskId(), d.getBlockedTaskId()))
            .toList();
    return DependencyGraph.of(open, edges);
  }

  /** Edges whose blocker is unfinished, for story precedences in sprint planning. */
  List<Edge> openBlockerEdges() {
    return dependencies.stream()
        .filter(d -> !done(tasks.get(d.getBlockerTaskId())))
        .map(d -> new Edge(d.getBlockerTaskId(), d.getBlockedTaskId()))
        .toList();
  }

  /**
   * PERT forecast for the unfinished {@code scope} tasks, including every unfinished task that
   * (transitively) blocks them, because the scope cannot finish before those.
   */
  Forecast forecast(DependencyGraph open, Collection<Long> scope, LocalDate start, LocalDate deadline) {
    Set<Long> openScope = new TreeSet<>();
    for (Long id : scope) if (open.nodes().contains(id)) openScope.add(id);
    DependencyGraph network = open.restrictedTo(open.withAncestors(openScope));
    CriticalPath path = CriticalPath.analyse(network, estimates);
    PertForecast pert = PertForecast.of(path, start, deadline);
    int estimated = (int) network.nodes().stream().filter(estimates::containsKey).count();
    return new Forecast(
        start,
        pert.expectedDays(),
        pert.standardDeviationDays(),
        pert.expectedFinish(),
        pert.probability(),
        estimated,
        network.nodes().size(),
        path.path().stream().map(tasks::get).map(RoadmapAssembler::ref).toList());
  }

  Roadmap roadmap(
      Collection<Epic> epics, Collection<UserStory> stories, Collection<Sprint> sprints, LocalDate today) {
    Map<Long, Sprint> sprintById = new TreeMap<>();
    sprints.forEach(s -> sprintById.put(s.getId(), s));
    Map<Long, List<Task>> tasksByStory = new TreeMap<>();
    tasks.values().stream()
        .filter(t -> t.getStoryId() != null)
        .forEach(t -> tasksByStory.computeIfAbsent(t.getStoryId(), k -> new ArrayList<>()).add(t));
    Map<Long, List<UserStory>> storiesByEpic = new TreeMap<>();
    Map<Long, Long> epicOfStory = new TreeMap<>();
    for (UserStory story : stories) {
      if (story.getEpicId() == null) continue;
      storiesByEpic.computeIfAbsent(story.getEpicId(), k -> new ArrayList<>()).add(story);
      epicOfStory.put(story.getId(), story.getEpicId());
    }
    DependencyGraph open = openGraph();
    List<RoadmapEpic> rows = new ArrayList<>();
    for (Epic epic : epics.stream().sorted(Comparator.comparing(Epic::getId)).toList()) {
      List<UserStory> members = storiesByEpic.getOrDefault(epic.getId(), List.of());
      int totalPoints = 0;
      int donePoints = 0;
      int taskCount = 0;
      int doneTasks = 0;
      Set<Long> sprintIds = new TreeSet<>();
      List<Long> scope = new ArrayList<>();
      for (UserStory story : members) {
        List<Task> storyTasks = tasksByStory.getOrDefault(story.getId(), List.of());
        long finished = storyTasks.stream().filter(RoadmapAssembler::done).count();
        totalPoints += story.getStoryPoints();
        if (!storyTasks.isEmpty() && finished == storyTasks.size()) donePoints += story.getStoryPoints();
        taskCount += storyTasks.size();
        doneTasks += (int) finished;
        if (story.getSprintId() != null) sprintIds.add(story.getSprintId());
        for (Task task : storyTasks) {
          scope.add(task.getId());
          if (task.getSprintId() != null) sprintIds.add(task.getSprintId());
        }
      }
      sprintIds.retainAll(sprintById.keySet());
      double progress =
          totalPoints > 0
              ? (double) donePoints / totalPoints
              : taskCount > 0 ? (double) doneTasks / taskCount : 0;
      LocalDate plannedStart = epic.getStartDate();
      LocalDate plannedEnd = epic.getTargetDate();
      for (Long sprintId : sprintIds) {
        Sprint sprint = sprintById.get(sprintId);
        if (epic.getStartDate() == null && sprint.getStartDate() != null)
          plannedStart = min(plannedStart, sprint.getStartDate());
        if (epic.getTargetDate() == null && sprint.getEndDate() != null)
          plannedEnd = max(plannedEnd, sprint.getEndDate());
      }
      LocalDate start = max(today, epic.getStartDate());
      rows.add(
          new RoadmapEpic(
              EpicView.of(epic),
              plannedStart,
              plannedEnd,
              members.size(),
              totalPoints,
              donePoints,
              taskCount,
              doneTasks,
              progress,
              members.stream().map(UserStory::getId).sorted().toList(),
              List.copyOf(sprintIds),
              forecast(open, scope, start, epic.getTargetDate())));
    }
    List<RoadmapSprint> sprintRows =
        sprintById.values().stream()
            .map(s -> new RoadmapSprint(s.getId(), s.getName(), s.getStatus(), s.getStartDate(), s.getEndDate()))
            .toList();
    return new Roadmap(
        today,
        List.copyOf(rows),
        sprintRows,
        epicLinks(epicOfStory),
        overview(sprints),
        forecast(open, open.nodes(), today, null));
  }

  private List<EpicLink> epicLinks(Map<Long, Long> epicOfStory) {
    Map<String, int[]> counts = new LinkedHashMap<>();
    Map<String, Long[]> ends = new TreeMap<>();
    for (TaskDependency dependency : dependencies) {
      Task blocker = tasks.get(dependency.getBlockerTaskId());
      Task target = tasks.get(dependency.getBlockedTaskId());
      Long from = blocker.getStoryId() == null ? null : epicOfStory.get(blocker.getStoryId());
      Long to = target.getStoryId() == null ? null : epicOfStory.get(target.getStoryId());
      if (from == null || to == null || Objects.equals(from, to)) continue;
      String key = String.format("%019d:%019d", from, to);
      ends.put(key, new Long[] {from, to});
      int[] count = counts.computeIfAbsent(key, k -> new int[2]);
      count[0]++;
      if (!done(blocker)) count[1]++;
    }
    return ends.entrySet().stream()
        .map(e -> new EpicLink(e.getValue()[0], e.getValue()[1], counts.get(e.getKey())[0], counts.get(e.getKey())[1]))
        .toList();
  }

  private static LocalDate min(LocalDate a, LocalDate b) {
    return a == null || b.isBefore(a) ? b : a;
  }

  private static LocalDate max(LocalDate a, LocalDate b) {
    if (a == null) return b;
    if (b == null) return a;
    return b.isAfter(a) ? b : a;
  }
}
