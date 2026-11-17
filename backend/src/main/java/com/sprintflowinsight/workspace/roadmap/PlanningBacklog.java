package com.sprintflowinsight.workspace.roadmap;

import java.util.List;
import java.util.Set;

/**
 * Backlog facts the sprint optimiser (C1) needs: business value, points, whether a story is done,
 * and story precedences lifted from task dependencies. It performs no authorisation; callers check
 * project access first.
 */
public interface PlanningBacklog {
  /** Live stories of the project in backlog order. */
  List<PlanningStory> stories(Long projectId);

  /**
   * @param businessValue 1–5, or {@code null} when the team has not valued the story
   * @param done the story has tasks and all of them are done
   * @param predecessorIds stories with unfinished tasks that block this story's tasks; acyclic
   */
  record PlanningStory(
      Long id, String title, int points, Integer businessValue, boolean done, Set<Long> predecessorIds) {}
}
