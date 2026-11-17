package com.sprintflowinsight.workspace.roadmap;

import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.DependencyOverview;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.EstimateView;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.Roadmap;
import com.sprintflowinsight.workspace.roadmap.schedule.StoryPrecedence;
import com.sprintflowinsight.workspace.sprint.SprintRepository;
import com.sprintflowinsight.workspace.story.UserStory;
import com.sprintflowinsight.workspace.story.UserStoryRepository;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskRepository;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of B3. Each read loads every table it needs exactly once (tasks, stories, epics,
 * sprints, dependencies, estimates), so the number of queries does not grow with the project.
 */
@Service
public class RoadmapService implements PlanningBacklog {
  private final ProjectAccessService access;
  private final EpicRepository epics;
  private final UserStoryRepository stories;
  private final TaskRepository tasks;
  private final SprintRepository sprints;
  private final TaskDependencyRepository dependencies;
  private final TaskEstimateRepository estimates;
  private final Clock clock = Clock.systemDefaultZone();

  RoadmapService(
      ProjectAccessService access,
      EpicRepository epics,
      UserStoryRepository stories,
      TaskRepository tasks,
      SprintRepository sprints,
      TaskDependencyRepository dependencies,
      TaskEstimateRepository estimates) {
    this.access = access;
    this.epics = epics;
    this.stories = stories;
    this.tasks = tasks;
    this.sprints = sprints;
    this.dependencies = dependencies;
    this.estimates = estimates;
  }

  @Transactional(readOnly = true)
  public Roadmap roadmap(Long projectId, HttpSession session) {
    access.read(projectId, session);
    return assembler(projectId)
        .roadmap(
            epics.findByProjectIdOrderByIdAsc(projectId),
            stories.findByProjectId(projectId),
            sprints.findByProjectId(projectId),
            LocalDate.now(clock));
  }

  @Transactional(readOnly = true)
  public DependencyOverview dependencies(Long projectId, HttpSession session) {
    access.read(projectId, session);
    return assembler(projectId).overview(sprints.findByProjectId(projectId));
  }

  @Transactional(readOnly = true)
  public List<EstimateView> estimates(Long projectId, HttpSession session) {
    access.read(projectId, session);
    var live = tasks.findByProjectId(projectId).stream().map(Task::getId).toList();
    var liveIds = new java.util.HashSet<>(live);
    return estimates.findByProjectId(projectId).stream()
        .filter(row -> liveIds.contains(row.getTaskId()))
        .sorted(java.util.Comparator.comparing(TaskEstimate::getTaskId))
        .map(DependencyService::view)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<PlanningStory> stories(Long projectId) {
    List<Task> projectTasks = tasks.findByProjectId(projectId);
    RoadmapAssembler assembler =
        new RoadmapAssembler(
            projectTasks, dependencies.findByProjectIdOrderByIdAsc(projectId), List.of());
    Map<Long, Long> storyOfTask = new HashMap<>();
    Map<Long, int[]> progress = new HashMap<>();
    for (Task task : projectTasks) {
      if (task.getStoryId() == null) continue;
      storyOfTask.put(task.getId(), task.getStoryId());
      int[] counts = progress.computeIfAbsent(task.getStoryId(), k -> new int[2]);
      counts[0]++;
      if (RoadmapAssembler.done(task)) counts[1]++;
    }
    Map<Long, Set<Long>> before =
        StoryPrecedence.predecessors(storyOfTask, assembler.openBlockerEdges());
    List<PlanningStory> result = new ArrayList<>();
    for (UserStory story : stories.findByProjectId(projectId)) {
      int[] counts = progress.get(story.getId());
      boolean done = counts != null && counts[0] == counts[1];
      result.add(
          new PlanningStory(
              story.getId(),
              story.getTitle(),
              story.getStoryPoints(),
              story.getBusinessValue(),
              done,
              before.getOrDefault(story.getId(), Set.of())));
    }
    return List.copyOf(result);
  }

  private RoadmapAssembler assembler(Long projectId) {
    return new RoadmapAssembler(
        tasks.findByProjectId(projectId),
        dependencies.findByProjectIdOrderByIdAsc(projectId),
        estimates.findByProjectId(projectId));
  }
}
