package com.sprintflowinsight.workspace.roadmap;

import java.time.LocalDate;
import java.util.List;

/** Response records for epics, dependencies and the roadmap. */
public final class RoadmapViews {
  private RoadmapViews() {}

  /** A task as it appears in dependency lists and critical paths. */
  public record TaskRef(Long id, String key, String title, String status, Long sprintId) {}

  public record EpicView(
      Long id,
      String title,
      String description,
      String color,
      LocalDate startDate,
      LocalDate targetDate,
      Long version) {
    static EpicView of(Epic epic) {
      return new EpicView(
          epic.getId(),
          epic.getTitle(),
          epic.getDescription(),
          epic.getColor(),
          epic.getStartDate(),
          epic.getTargetDate(),
          epic.getVersion());
    }
  }

  public record StoryEpicView(Long storyId, Long epicId, Long version) {}

  public record DependencyView(Long id, TaskRef blocker, TaskRef blocked, boolean open) {}

  /**
   * A task in the active sprint that waits for unfinished work outside that sprint.
   *
   * @param blocker the outside task; {@code blocker.sprintId} is {@code null} for backlog work
   */
  public record ExternalBlock(TaskRef task, TaskRef blocker) {}

  /**
   * All dependencies of a project with derived board state.
   *
   * @param blockedTaskIds unfinished tasks with at least one unfinished blocker
   * @param externalBlocks warnings for the active sprint
   */
  public record DependencyOverview(
      List<DependencyView> dependencies,
      List<Long> blockedTaskIds,
      List<ExternalBlock> externalBlocks) {}

  public record EstimateView(
      Long taskId,
      TaskRef task,
      double optimistic,
      double mostLikely,
      double pessimistic,
      double expected,
      double standardDeviation,
      Long version) {}

  /**
   * PERT forecast for remaining work.
   *
   * @param probabilityOnTime approximate {@code P(finish <= targetDate)}; {@code null} without a
   *     target date or when estimates are incomplete
   * @param estimatedTasks open tasks in the network that have a three-point estimate
   * @param openTasks open tasks in the network, including outside blockers
   * @param unplannedStories stories without tasks; these prevent a complete forecast
   * @param criticalPath the longest chain of open work, first task first
   */
  public record Forecast(
      LocalDate start,
      double expectedDays,
      double standardDeviationDays,
      LocalDate expectedFinish,
      Double probabilityOnTime,
      int estimatedTasks,
      int openTasks,
      int unplannedStories,
      List<TaskRef> criticalPath) {}

  /**
   * One epic on the roadmap.
   *
   * @param plannedStart explicit start date, or the earliest start of the sprints its stories use
   * @param plannedEnd explicit target date, or the latest end of those sprints
   * @param progress share of finished story points (finished tasks when no story has points)
   */
  public record RoadmapEpic(
      EpicView epic,
      LocalDate plannedStart,
      LocalDate plannedEnd,
      int storyCount,
      int totalPoints,
      int donePoints,
      int taskCount,
      int doneTaskCount,
      double progress,
      List<Long> storyIds,
      List<Long> sprintIds,
      Forecast forecast) {}

  public record RoadmapSprint(
      Long id, String name, String status, LocalDate startDate, LocalDate endDate) {}

  /** Arrow on the roadmap: tasks in {@code fromEpicId} block tasks in {@code toEpicId}. */
  public record EpicLink(Long fromEpicId, Long toEpicId, int dependencies, int open) {}

  public record Roadmap(
      LocalDate today,
      List<RoadmapEpic> epics,
      List<RoadmapSprint> sprints,
      List<EpicLink> epicLinks,
      DependencyOverview dependencies,
      Forecast project) {}
}
