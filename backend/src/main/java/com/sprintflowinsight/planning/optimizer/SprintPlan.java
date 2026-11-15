package com.sprintflowinsight.planning.optimizer;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * A proposed sprint.
 *
 * @param solver algorithm that produced the plan
 * @param status what is known about the plan's quality
 * @param selectedStoryIds chosen stories in backlog order; empty when infeasible
 * @param objective total business value of the chosen stories
 * @param bestBound proven upper bound on the objective, or {@code null} when the solver gives none
 *     (greedy) or the problem is infeasible
 * @param relativeGap {@code (bestBound - objective) / bestBound}, 0 when the bound is 0, or {@code
 *     null} without a bound
 * @param wallTime time spent solving
 * @param explanation binding resources and reasons for excluded stories
 */
public record SprintPlan(
    SolverKind solver,
    SolveStatus status,
    List<String> selectedStoryIds,
    long objective,
    Long bestBound,
    Double relativeGap,
    Duration wallTime,
    PlanExplanation explanation) {

  public SprintPlan {
    Objects.requireNonNull(solver, "solver");
    Objects.requireNonNull(status, "status");
    selectedStoryIds = List.copyOf(selectedStoryIds);
    Objects.requireNonNull(wallTime, "wallTime");
    Objects.requireNonNull(explanation, "explanation");
  }

  /** Relative optimality gap for a bound; 0 when both are 0. */
  static double gap(long objective, long bound) {
    return bound == 0 ? 0.0 : (double) (bound - objective) / Math.abs(bound);
  }
}
