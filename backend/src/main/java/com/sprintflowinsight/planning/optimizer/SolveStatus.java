package com.sprintflowinsight.planning.optimizer;

/** How much the solver knows about its plan. */
public enum SolveStatus {
  /** Proven optimal. */
  OPTIMAL,
  /** Satisfies every constraint; optimality is not proven (time limit or heuristic). */
  FEASIBLE,
  /** No plan satisfies the locks and capacities; the explanation lists the conflicts. */
  INFEASIBLE,
  /**
   * The solver stopped before finding a plan. The returned plan is the greedy fallback, which is
   * still feasible.
   */
  UNKNOWN
}
