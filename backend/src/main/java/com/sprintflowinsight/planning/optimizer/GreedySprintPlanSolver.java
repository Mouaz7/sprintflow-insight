package com.sprintflowinsight.planning.optimizer;

import java.time.Duration;

/**
 * Baseline heuristic: start from the locked-in stories, then repeatedly add the story (together
 * with its unselected predecessors) that has the highest value per point and still fits every
 * capacity. Ties prefer the higher value, then backlog order. Stops when nothing with positive
 * value fits. O(n² · d) for n stories and dependency depth d; no optimality guarantee.
 */
public final class GreedySprintPlanSolver implements SprintPlanSolver {

  @Override
  public SolverKind kind() {
    return SolverKind.GREEDY;
  }

  @Override
  public SprintPlan solve(PlanningProblem problem) {
    long started = System.nanoTime();
    var analysis = new ProblemAnalysis(problem);
    if (analysis.infeasible()) {
      return analysis.infeasiblePlan(kind(), elapsed(started));
    }
    boolean[] selected = select(analysis);
    return analysis.plan(kind(), SolveStatus.FEASIBLE, selected, null, elapsed(started));
  }

  /** The greedy selection for a feasible analysis; also used as CP-SAT's hint and fallback. */
  static boolean[] select(ProblemAnalysis analysis) {
    boolean[] selected = analysis.forced.clone();
    long[] used = analysis.usage(selected);
    while (true) {
      int[] best = null;
      long bestValue = 0;
      double bestDensity = 0;
      for (int i = 0; i < analysis.size; i++) {
        if (selected[i] || !analysis.selectable(i)) {
          continue;
        }
        int[] bundle = analysis.bundle(i, selected);
        long value = analysis.value(bundle);
        if (value == 0 || !analysis.exceeded(used, bundle).isEmpty()) {
          continue;
        }
        double density = ProblemAnalysis.density(value, analysis.points(bundle));
        if (best == null
            || density > bestDensity
            || (density == bestDensity && value > bestValue)) {
          best = bundle;
          bestValue = value;
          bestDensity = density;
        }
      }
      if (best == null) {
        return selected;
      }
      for (int i : best) {
        selected[i] = true;
      }
      used = analysis.usage(selected);
    }
  }

  static Duration elapsed(long startedNanos) {
    return Duration.ofNanos(System.nanoTime() - startedNanos);
  }
}
