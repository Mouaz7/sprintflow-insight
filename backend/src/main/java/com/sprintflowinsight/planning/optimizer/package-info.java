/**
 * Sprint content optimisation: choose stories that maximise business value under total and
 * per-member capacity, precedence dependencies and locks. Three solvers share {@link
 * com.sprintflowinsight.planning.optimizer.SprintPlanSolver}: CP-SAT (OR-Tools), a greedy
 * value-density baseline and an exact dynamic program for single-capacity forests. See ADR 0011.
 */
@org.springframework.modulith.NamedInterface("optimizer")
package com.sprintflowinsight.planning.optimizer;
