package com.sprintflowinsight.planning.optimizer;

/** Chooses sprint content for a {@link PlanningProblem}. Implementations are thread-safe. */
public interface SprintPlanSolver {

  SolverKind kind();

  /** False when this solver cannot solve the problem exactly (see the implementation). */
  default boolean supports(PlanningProblem problem) {
    return true;
  }

  /**
   * Returns a plan that respects every capacity, dependency and lock, or an {@link
   * SolveStatus#INFEASIBLE} plan when none exists.
   *
   * @throws IllegalArgumentException when {@link #supports} is false
   */
  SprintPlan solve(PlanningProblem problem);
}
