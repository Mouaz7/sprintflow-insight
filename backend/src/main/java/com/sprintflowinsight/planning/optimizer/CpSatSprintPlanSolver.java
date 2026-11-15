package com.sprintflowinsight.planning.optimizer;

import com.google.ortools.Loader;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.LinearArgument;
import com.google.ortools.sat.LinearExpr;
import java.util.ArrayList;
import java.util.List;

/**
 * Exact model in OR-Tools CP-SAT. One boolean per selectable story; locked-in stories and their
 * predecessors are fixed to true; {@code x[s] ⇒ x[p]} for every candidate predecessor; one linear
 * capacity row for the total and for each member; maximise total value. Blocked stories get no
 * variable. The greedy plan is passed as a solution hint.
 *
 * <p>Ties between plans of equal value are broken lexicographically, in a second phase within the
 * same time limit: with the value fixed at the proven optimum, the free (selectable, not forced)
 * stories are visited in backlog order and each prefers to be <em>in</em> the plan when it has
 * value and <em>out</em> when it has none. The phase maximises {@code Σ ±2^k·x} over consecutive
 * blocks of {@value #TIE_BREAK_BLOCK} stories (earlier block first, each block's optimum fixed
 * before the next), so every block objective is injective and stays below {@link
 * #MAX_EXACT_OBJECTIVE}. The preferred plan is therefore unique, and the result is reproducible
 * even with parallel workers whenever every phase proves optimality. A story without value is never
 * chosen unless a valuable successor needs it. If the time limit cuts the tie-break short, the
 * best plan found so far is kept; its value is still optimal.
 *
 * <p>Status mapping: CP-SAT OPTIMAL/FEASIBLE are reported as is, with its best bound and relative
 * gap. If the time limit expires before any solution, the greedy plan is returned with status
 * {@link SolveStatus#UNKNOWN} and no bound. The model is always feasible after the shared lock
 * analysis, so CP-SAT's INFEASIBLE cannot occur.
 */
public final class CpSatSprintPlanSolver implements SprintPlanSolver {
  private final CpSatSettings settings;

  public CpSatSprintPlanSolver() {
    this(CpSatSettings.defaults());
  }

  public CpSatSprintPlanSolver(CpSatSettings settings) {
    this.settings = settings;
    Loader.loadNativeLibraries();
  }

  public CpSatSettings settings() {
    return settings;
  }

  @Override
  public SolverKind kind() {
    return SolverKind.CP_SAT;
  }

  @Override
  public SprintPlan solve(PlanningProblem problem) {
    long started = System.nanoTime();
    var analysis = new ProblemAnalysis(problem);
    if (analysis.infeasible()) {
      return analysis.infeasiblePlan(kind(), GreedySprintPlanSolver.elapsed(started));
    }
    boolean[] hint = GreedySprintPlanSolver.select(analysis);
    var model = new CpModel();
    BoolVar[] chosen = new BoolVar[analysis.size];
    for (int i = 0; i < analysis.size; i++) {
      if (analysis.selectable(i)) {
        chosen[i] = model.newBoolVar(analysis.stories.get(i).id());
        model.addHint(chosen[i], hint[i]);
        if (analysis.forced[i]) {
          model.addEquality(chosen[i], 1);
        }
      }
    }
    for (int i = 0; i < analysis.size; i++) {
      if (chosen[i] != null) {
        for (int p : analysis.predecessors[i]) {
          model.addImplication(chosen[i], chosen[p]);
        }
      }
    }
    for (int r = 0; r < analysis.resources.size(); r++) {
      model.addLessOrEqual(weighted(chosen, analysis.demand[r]), analysis.capacities[r]);
    }
    long[] values = new long[analysis.size];
    for (int i = 0; i < analysis.size; i++) {
      values[i] = analysis.stories.get(i).value();
    }
    LinearExpr totalValue = weighted(chosen, values);
    model.maximize(totalValue);

    long deadline = started + settings.timeLimit().toNanos();
    var solver = new CpSolver();
    solver
        .getParameters()
        .setNumWorkers(settings.workers())
        .setRandomSeed(settings.randomSeed())
        .setInterleaveSearch(settings.interleaveSearch())
        .setLogSearchProgress(false);
    CpSolverStatus status = solve(solver, model, deadline);
    if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
      // Without a solution CP-SAT's bound is not meaningful; return the greedy plan unbounded.
      return analysis.plan(
          kind(), SolveStatus.UNKNOWN, hint, null, GreedySprintPlanSolver.elapsed(started));
    }
    boolean[] selected = read(solver, chosen);
    long objective = 0;
    for (int i = 0; i < analysis.size; i++) {
      objective += selected[i] ? values[i] : 0;
    }
    long bound = Math.max(objective, (long) Math.floor(solver.bestObjectiveBound() + 1e-6));
    if (status == CpSolverStatus.OPTIMAL) {
      model.addGreaterOrEqual(totalValue, objective);
      selected = breakTies(analysis, model, solver, chosen, selected, deadline);
    }
    return analysis.plan(
        kind(),
        status == CpSolverStatus.OPTIMAL ? SolveStatus.OPTIMAL : SolveStatus.FEASIBLE,
        selected,
        bound,
        GreedySprintPlanSolver.elapsed(started));
  }

  /**
   * Second phase: among plans of the optimal value (already constrained in the model), find the
   * lexicographically preferred one, block by block. Returns the best selection reached before the
   * deadline.
   */
  static boolean[] breakTies(
      ProblemAnalysis analysis,
      CpModel model,
      CpSolver solver,
      BoolVar[] chosen,
      boolean[] selected,
      long deadline) {
    List<Integer> free = new ArrayList<>();
    for (int i = 0; i < analysis.size; i++) {
      if (chosen[i] != null && !analysis.forced[i]) {
        free.add(i);
      }
    }
    boolean[] best = selected;
    for (int from = 0; from < free.size(); from += TIE_BREAK_BLOCK) {
      long[] preference = preferenceBlock(analysis, free, from);
      LinearExpr block = weighted(chosen, preference);
      model.clearObjective();
      model.maximize(block);
      model.clearHints();
      for (int i = 0; i < analysis.size; i++) {
        if (chosen[i] != null) {
          model.addHint(chosen[i], best[i]);
        }
      }
      CpSolverStatus status = solve(solver, model, deadline);
      if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
        return best;
      }
      best = read(solver, chosen);
      if (status != CpSolverStatus.OPTIMAL) {
        return best;
      }
      long reached = 0;
      for (int i = 0; i < analysis.size; i++) {
        reached += best[i] ? preference[i] : 0;
      }
      model.addEquality(block, reached);
    }
    return best;
  }

  /**
   * Tie-break weights for the free stories {@code free[from..from+TIE_BREAK_BLOCK)}: distinct
   * powers of two, highest first, positive for stories with value and negative for stories
   * without. The absolute sum stays below {@link #MAX_EXACT_OBJECTIVE}.
   */
  static long[] preferenceBlock(ProblemAnalysis analysis, List<Integer> free, int from) {
    long[] preference = new long[analysis.size];
    int to = Math.min(free.size(), from + TIE_BREAK_BLOCK);
    for (int k = from; k < to; k++) {
      int story = free.get(k);
      long weight = 1L << (to - 1 - k);
      preference[story] = analysis.stories.get(story).value() > 0 ? weight : -weight;
    }
    return preference;
  }

  /** Solves with the time left until the deadline; UNKNOWN when none is left. */
  private static CpSolverStatus solve(CpSolver solver, CpModel model, long deadline) {
    long left = deadline - System.nanoTime();
    if (left <= 0) {
      return CpSolverStatus.UNKNOWN;
    }
    solver.getParameters().setMaxTimeInSeconds(left / 1e9);
    return solver.solve(model);
  }

  private static boolean[] read(CpSolver solver, BoolVar[] chosen) {
    boolean[] selected = new boolean[chosen.length];
    for (int i = 0; i < chosen.length; i++) {
      selected[i] = chosen[i] != null && solver.booleanValue(chosen[i]);
    }
    return selected;
  }

  /** Stories per tie-break block; {@code 2^TIE_BREAK_BLOCK − 1 < MAX_EXACT_OBJECTIVE}. */
  static final int TIE_BREAK_BLOCK = 50;

  /** CP-SAT reports objectives as doubles; stay well inside their 53-bit integer range. */
  static final long MAX_EXACT_OBJECTIVE = 1L << 50;

  private static LinearExpr weighted(BoolVar[] variables, long[] coefficients) {
    List<LinearArgument> terms = new ArrayList<>();
    List<Long> weights = new ArrayList<>();
    for (int i = 0; i < variables.length; i++) {
      if (variables[i] != null && coefficients[i] != 0) {
        terms.add(variables[i]);
        weights.add(coefficients[i]);
      }
    }
    return LinearExpr.weightedSum(
        terms.toArray(LinearArgument[]::new), weights.stream().mapToLong(Long::longValue).toArray());
  }
}
