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
 * variable. The greedy plan is passed as a solution hint. Ties between plans of equal value are
 * broken towards earlier backlog positions (see {@link #tieBreakScale}), so results are
 * reproducible even with parallel workers whenever the search proves optimality.
 *
 * <p>Status mapping: CP-SAT OPTIMAL/FEASIBLE are reported as is, with its best bound and relative
 * gap. If the time limit expires before any solution, the greedy plan is returned with status
 * {@link SolveStatus#UNKNOWN} and no bound. The model is always feasible after the shared lock analysis, so
 * CP-SAT's INFEASIBLE cannot occur.
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
    long scale = tieBreakScale(analysis);
    long[] weights = new long[analysis.size];
    for (int i = 0; i < analysis.size; i++) {
      long preference = scale == 1 ? 0 : analysis.size - i;
      weights[i] = analysis.stories.get(i).value() * scale + preference;
    }
    model.maximize(weighted(chosen, weights));

    var solver = new CpSolver();
    solver
        .getParameters()
        .setMaxTimeInSeconds(settings.timeLimit().toNanos() / 1e9)
        .setNumWorkers(settings.workers())
        .setRandomSeed(settings.randomSeed())
        .setInterleaveSearch(settings.interleaveSearch())
        .setLogSearchProgress(false);
    CpSolverStatus status = solver.solve(model);
    if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
      // Without a solution CP-SAT's bound is not meaningful; return the greedy plan unbounded.
      return analysis.plan(
          kind(), SolveStatus.UNKNOWN, hint, null, GreedySprintPlanSolver.elapsed(started));
    }
    long bound = (long) Math.floor((solver.bestObjectiveBound() + 1e-6) / scale);
    boolean[] selected = new boolean[analysis.size];
    for (int i = 0; i < analysis.size; i++) {
      selected[i] = chosen[i] != null && solver.booleanValue(chosen[i]);
    }
    return analysis.plan(
        kind(),
        status == CpSolverStatus.OPTIMAL ? SolveStatus.OPTIMAL : SolveStatus.FEASIBLE,
        selected,
        bound,
        GreedySprintPlanSolver.elapsed(started));
  }

  /**
   * Multiplier that makes value dominate a backlog-order preference: among plans of equal value
   * CP-SAT then prefers earlier stories, so parallel workers converge on the same plan. Falls back
   * to 1 (no preference) when the scaled objective would leave CP-SAT's exact double range.
   */
  static long tieBreakScale(ProblemAnalysis analysis) {
    long n = analysis.size;
    long scale = n * (n + 1) / 2 + 1;
    long totalValue = 0;
    for (CandidateStory story : analysis.stories) {
      totalValue += story.value();
    }
    return totalValue + 1 <= MAX_EXACT_OBJECTIVE / scale ? scale : 1;
  }

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
