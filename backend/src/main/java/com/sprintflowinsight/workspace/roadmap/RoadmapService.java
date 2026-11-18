package com.sprintflowinsight.workspace.roadmap;

import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.DependencyOverview;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.EstimateView;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.Roadmap;
import com.sprintflowinsight.workspace.sprint.SprintRepository;
import com.sprintflowinsight.workspace.story.UserStoryRepository;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskRepository;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of B3. Each read loads every table it needs exactly once (tasks, stories, epics,
 * sprints, dependencies, estimates), so the number of queries does not grow with the project.
 */
@Service
public class RoadmapService {
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
    var live =
        tasks.findByProjectId(projectId).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    Task::getId, java.util.function.Function.identity()));
    return estimates.findByProjectId(projectId).stream()
        .filter(TaskEstimate::isActive)
        .filter(row -> live.containsKey(row.getTaskId()))
        .sorted(java.util.Comparator.comparing(TaskEstimate::getTaskId))
        .map(row -> DependencyService.view(row, live.get(row.getTaskId())))
        .toList();
  }

  private RoadmapAssembler assembler(Long projectId) {
    return new RoadmapAssembler(
        tasks.findByProjectId(projectId),
        dependencies.findByProjectIdOrderByIdAsc(projectId),
        estimates.findByProjectId(projectId));
  }
}
