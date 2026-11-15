package com.sprintflowinsight.planning.optimizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Exact baseline for one resource: a 0/1 knapsack over the total capacity in pseudo-polynomial
 * time O(n · C).
 *
 * <p>Dependencies are handled as a precedence forest ("tree knapsack"). Locked-out stories and
 * stories with unsatisfiable dependencies are filtered out first; locked-in stories and their
 * predecessors are fixed and their points subtracted from the capacity. Each remaining story may
 * then have at most one remaining (free) predecessor, which becomes its parent. Stories are laid
 * out in depth-first preorder and {@code best[i][c]} is the best value from positions {@code i..}
 * with capacity {@code c}: either take story {@code i} (its subtree stays available) or skip it
 * together with its whole subtree.
 *
 * <p>Limits, reported by {@link #supports}: no per-member capacities, no story with two or more
 * free predecessors (general precedence-constrained knapsack is strongly NP-hard), and at most
 * {@value #MAX_TABLE_CELLS} table cells.
 */
public final class DynamicProgrammingSprintPlanSolver implements SprintPlanSolver {
  static final long MAX_TABLE_CELLS = 20_000_000L;

  @Override
  public SolverKind kind() {
    return SolverKind.DYNAMIC_PROGRAMMING;
  }

  @Override
  public boolean supports(PlanningProblem problem) {
    if (problem.hasMemberCapacities()) {
      return false;
    }
    var analysis = new ProblemAnalysis(problem);
    return analysis.infeasible() || layout(analysis) != null;
  }

  @Override
  public SprintPlan solve(PlanningProblem problem) {
    long started = System.nanoTime();
    if (problem.hasMemberCapacities()) {
      throw new IllegalArgumentException("Dynamic programming supports total capacity only");
    }
    var analysis = new ProblemAnalysis(problem);
    if (analysis.infeasible()) {
      return analysis.infeasiblePlan(kind(), GreedySprintPlanSolver.elapsed(started));
    }
    Layout layout = layout(analysis);
    if (layout == null) {
      throw new IllegalArgumentException(
          "Dynamic programming needs at most one free predecessor per story and a bounded table");
    }
    boolean[] selected = analysis.forced.clone();
    int[] order = layout.preorder;
    int n = order.length;
    int capacity = layout.capacity;
    long[][] best = new long[n + 1][capacity + 1];
    for (int i = n - 1; i >= 0; i--) {
      int story = order[i];
      int weight = analysis.stories.get(story).points();
      long value = analysis.stories.get(story).value();
      for (int c = 0; c <= capacity; c++) {
        long skip = best[i + layout.subtreeSize[i]][c];
        long take = weight <= c ? value + best[i + 1][c - weight] : Long.MIN_VALUE;
        best[i][c] = Math.max(skip, take);
      }
    }
    int i = 0;
    int c = capacity;
    while (i < n) {
      int story = order[i];
      int weight = analysis.stories.get(story).points();
      long skip = best[i + layout.subtreeSize[i]][c];
      if (weight <= c && analysis.stories.get(story).value() + best[i + 1][c - weight] > skip) {
        selected[story] = true;
        c -= weight;
        i++;
      } else {
        i += layout.subtreeSize[i];
      }
    }
    var plan =
        analysis.plan(
            kind(), SolveStatus.OPTIMAL, selected, null, GreedySprintPlanSolver.elapsed(started));
    return new SprintPlan(
        plan.solver(),
        plan.status(),
        plan.selectedStoryIds(),
        plan.objective(),
        plan.objective(),
        0.0,
        plan.wallTime(),
        plan.explanation());
  }

  private record Layout(int[] preorder, int[] subtreeSize, int capacity) {}

  /** Preorder of the free stories' forest, or null when the problem is outside the DP's limits. */
  private static Layout layout(ProblemAnalysis analysis) {
    int n = analysis.size;
    long forcedPoints = analysis.usage(analysis.forced)[0];
    int capacity = (int) (analysis.capacities[0] - forcedPoints);
    List<List<Integer>> children = new ArrayList<>();
    for (int i = 0; i <= n; i++) {
      children.add(new ArrayList<>());
    }
    int free = 0;
    for (int i = 0; i < n; i++) {
      if (analysis.forced[i] || !analysis.selectable(i)) {
        continue;
      }
      free++;
      int parent = n;
      for (int p : analysis.predecessors[i]) {
        if (!analysis.forced[p]) {
          if (parent != n) {
            return null;
          }
          parent = p;
        }
      }
      children.get(parent).add(i);
    }
    if ((long) (free + 1) * (capacity + 1) > MAX_TABLE_CELLS) {
      return null;
    }
    int[] preorder = new int[free];
    int[] subtreeSize = new int[free];
    int[] next = {0};
    for (int root : children.get(n)) {
      visit(root, children, preorder, subtreeSize, next);
    }
    return new Layout(preorder, subtreeSize, capacity);
  }

  private static void visit(
      int story, List<List<Integer>> children, int[] preorder, int[] subtreeSize, int[] next) {
    int position = next[0]++;
    preorder[position] = story;
    for (int child : children.get(story)) {
      visit(child, children, preorder, subtreeSize, next);
    }
    subtreeSize[position] = next[0] - position;
  }
}
