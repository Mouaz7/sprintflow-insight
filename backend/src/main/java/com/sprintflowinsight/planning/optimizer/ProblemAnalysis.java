package com.sprintflowinsight.planning.optimizer;

import com.sprintflowinsight.planning.optimizer.PlanExplanation.ConflictKind;
import com.sprintflowinsight.planning.optimizer.PlanExplanation.ExclusionReason;
import com.sprintflowinsight.planning.optimizer.PlanExplanation.PlanConflict;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Solver-independent facts about a problem: which stories can never be planned, which are forced
 * by locks, the capacity rows and whether the locks alone are infeasible. All solvers share it, so
 * that they agree on feasibility and explanations.
 */
final class ProblemAnalysis {
  static final String TOTAL = "total";
  static final String MEMBER_PREFIX = "member:";

  final PlanningProblem problem;
  final List<CandidateStory> stories;
  final int size;
  /** Indexes of each story's predecessors that are candidates (done ones are satisfied). */
  final int[][] predecessors;
  final List<Integer> topologicalOrder;
  final List<String> resources;
  final long[] capacities;
  /** demand[resource][story]. */
  final long[][] demand;
  /** Reason a story can never be planned, or null when it is selectable. */
  final ExclusionReason[] blockedReason;
  final List<List<String>> blockers;
  /** Locked-in stories and all their candidate predecessors. */
  final boolean[] forced;
  final List<PlanConflict> conflicts;

  ProblemAnalysis(PlanningProblem problem) {
    this.problem = problem;
    this.stories = problem.stories();
    this.size = stories.size();
    Map<String, Integer> index = new HashMap<>();
    for (int i = 0; i < size; i++) {
      index.put(stories.get(i).id(), i);
    }
    predecessors = new int[size][];
    for (int i = 0; i < size; i++) {
      predecessors[i] =
          stories.get(i).predecessorIds().stream()
              .filter(index::containsKey)
              .mapToInt(index::get)
              .toArray();
    }
    topologicalOrder = PlanningProblem.topologicalOrder(stories, index);

    resources = new ArrayList<>();
    resources.add(TOTAL);
    problem.memberCapacities().keySet().forEach(member -> resources.add(MEMBER_PREFIX + member));
    capacities = new long[resources.size()];
    demand = new long[resources.size()][size];
    capacities[0] = problem.totalCapacity();
    int r = 1;
    for (var member : problem.memberCapacities().entrySet()) {
      capacities[r] = member.getValue();
      for (int i = 0; i < size; i++) {
        demand[r][i] = stories.get(i).effortByMember().getOrDefault(member.getKey(), 0);
      }
      r++;
    }
    for (int i = 0; i < size; i++) {
      demand[0][i] = stories.get(i).points();
    }

    blockedReason = new ExclusionReason[size];
    blockers = new ArrayList<>(Collections.nCopies(size, List.of()));
    for (int i : topologicalOrder) {
      CandidateStory story = stories.get(i);
      if (story.lock() == Lock.LOCKED_OUT) {
        blockedReason[i] = ExclusionReason.LOCKED_OUT;
        continue;
      }
      List<String> missing = new ArrayList<>();
      for (String predecessor : story.predecessorIds()) {
        Integer p = index.get(predecessor);
        boolean satisfied =
            p == null ? problem.doneStoryIds().contains(predecessor) : blockedReason[p] == null;
        if (!satisfied) {
          missing.add(predecessor);
        }
      }
      if (!missing.isEmpty()) {
        blockedReason[i] = ExclusionReason.MISSING_DEPENDENCY;
        blockers.set(i, List.copyOf(missing));
      }
    }

    forced = new boolean[size];
    for (int i = 0; i < size; i++) {
      if (stories.get(i).lock() == Lock.LOCKED_IN) {
        markWithPredecessors(i, forced);
      }
    }
    conflicts = findConflicts();
  }

  boolean infeasible() {
    return !conflicts.isEmpty();
  }

  boolean selectable(int story) {
    return blockedReason[story] == null;
  }

  private void markWithPredecessors(int story, boolean[] marks) {
    if (marks[story]) {
      return;
    }
    marks[story] = true;
    for (int p : predecessors[story]) {
      markWithPredecessors(p, marks);
    }
  }

  private List<PlanConflict> findConflicts() {
    List<PlanConflict> found = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      if (stories.get(i).lock() == Lock.LOCKED_IN && !selectable(i)) {
        found.add(
            new PlanConflict(
                ConflictKind.LOCKED_IN_BLOCKED, List.of(stories.get(i).id()), blockers.get(i), null));
      }
    }
    long[] used = usage(forced);
    for (int r = 0; r < resources.size(); r++) {
      if (used[r] > capacities[r]) {
        List<String> involved = new ArrayList<>();
        for (int i = 0; i < size; i++) {
          if (forced[i] && demand[r][i] > 0) {
            involved.add(stories.get(i).id());
          }
        }
        found.add(
            new PlanConflict(
                ConflictKind.LOCKED_IN_OVER_CAPACITY, involved, List.of(), resources.get(r)));
      }
    }
    return List.copyOf(found);
  }

  /** Capacity used per resource by the selected stories. */
  long[] usage(boolean[] selected) {
    long[] used = new long[resources.size()];
    for (int i = 0; i < size; i++) {
      if (selected[i]) {
        for (int r = 0; r < used.length; r++) {
          used[r] += demand[r][i];
        }
      }
    }
    return used;
  }

  /** The story plus its unselected candidate predecessors, transitively, in index order. */
  int[] bundle(int story, boolean[] selected) {
    boolean[] marks = selected.clone();
    boolean[] before = selected.clone();
    markWithPredecessors(story, marks);
    List<Integer> result = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      if (marks[i] && !before[i]) {
        result.add(i);
      }
    }
    return result.stream().mapToInt(Integer::intValue).toArray();
  }

  /** Resources that the bundle would exceed on top of the current usage. */
  List<String> exceeded(long[] used, int[] bundle) {
    List<String> result = new ArrayList<>();
    for (int r = 0; r < resources.size(); r++) {
      long extra = 0;
      for (int i : bundle) {
        extra += demand[r][i];
      }
      if (used[r] + extra > capacities[r]) {
        result.add(resources.get(r));
      }
    }
    return result;
  }

  long value(int[] bundle) {
    long total = 0;
    for (int i : bundle) {
      total += stories.get(i).value();
    }
    return total;
  }

  long points(int[] bundle) {
    long total = 0;
    for (int i : bundle) {
      total += stories.get(i).points();
    }
    return total;
  }

  /** Value per point; a story without points but with value is infinitely dense. */
  static double density(long value, long points) {
    if (points == 0) {
      return value == 0 ? 0.0 : Double.POSITIVE_INFINITY;
    }
    return (double) value / points;
  }

  /** Builds the plan record, with objective and explanation, for a feasible selection. */
  SprintPlan plan(
      SolverKind solver, SolveStatus status, boolean[] selected, Long bound, Duration time) {
    List<String> ids = new ArrayList<>();
    long objective = 0;
    for (int i = 0; i < size; i++) {
      if (selected[i]) {
        ids.add(stories.get(i).id());
        objective += stories.get(i).value();
      }
    }
    Double gap = bound == null ? null : SprintPlan.gap(objective, bound);
    return new SprintPlan(
        solver, status, ids, objective, bound, gap, time, PlanExplainer.explain(this, selected));
  }

  /** The plan for a problem whose locks cannot be satisfied. */
  SprintPlan infeasiblePlan(SolverKind solver, Duration time) {
    return new SprintPlan(
        solver,
        SolveStatus.INFEASIBLE,
        List.of(),
        0,
        null,
        null,
        time,
        PlanExplainer.explainInfeasible(this));
  }
}
