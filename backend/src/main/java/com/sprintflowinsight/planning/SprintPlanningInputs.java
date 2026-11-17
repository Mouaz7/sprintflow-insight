package com.sprintflowinsight.planning;

import com.sprintflowinsight.workspace.roadmap.PlanningBacklog;
import org.springframework.stereotype.Service;

/**
 * Reads a project's backlog for the optimiser. Performs no authorisation: the C1 endpoint checks
 * project access before calling it.
 */
@Service
public class SprintPlanningInputs {
  private final PlanningBacklog backlog;

  public SprintPlanningInputs(PlanningBacklog backlog) {
    this.backlog = backlog;
  }

  public BacklogCandidates candidates(Long projectId) {
    return BacklogCandidates.of(backlog.stories(projectId));
  }
}
