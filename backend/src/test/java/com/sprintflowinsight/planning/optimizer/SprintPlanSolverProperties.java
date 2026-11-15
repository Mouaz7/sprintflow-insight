package com.sprintflowinsight.planning.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.planning.optimizer.PlanExplanation.ExcludedStory;
import com.sprintflowinsight.planning.optimizer.PlanExplanation.ExclusionReason;
import com.sprintflowinsight.planning.optimizer.PlanExplanation.ResourceUsage;
import com.sprintflowinsight.planning.optimizer.RandomBacklogs.Shape;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.statistics.Statistics;
import net.jqwik.api.constraints.IntRange;

/** Solver invariants on random backlogs. */
class SprintPlanSolverProperties {
  private static final CpSatSprintPlanSolver CP_SAT = new CpSatSprintPlanSolver();
  private static final GreedySprintPlanSolver GREEDY = new GreedySprintPlanSolver();
  private static final DynamicProgrammingSprintPlanSolver DP =
      new DynamicProgrammingSprintPlanSolver();

  @Property(tries = 300)
  void everyPlanRespectsCapacitiesDependenciesAndLocks(
      @ForAll long seed,
      @ForAll @IntRange(min = 0, max = 40) int stories,
      @ForAll @IntRange(min = 0, max = 4) int members) {
    PlanningProblem problem = RandomBacklogs.generate(seed, Shape.general(stories, members));
    for (SprintPlanSolver solver : List.of(CP_SAT, GREEDY)) {
      checkPlan(problem, solver.solve(problem));
    }
  }

  @Property(tries = 300)
  void dynamicProgramRespectsTheSameRules(
      @ForAll long seed, @ForAll @IntRange(min = 0, max = 40) int stories) {
    PlanningProblem problem = RandomBacklogs.generate(seed, Shape.singleCapacity(stories));
    assertThat(DP.supports(problem)).isTrue();
    checkPlan(problem, DP.solve(problem));
  }

  @Property(tries = 300)
  void cpSatIsOptimalAndAtLeastAsGoodAsGreedy(
      @ForAll long seed,
      @ForAll @IntRange(min = 0, max = 30) int stories,
      @ForAll @IntRange(min = 0, max = 3) int members) {
    PlanningProblem problem = RandomBacklogs.generate(seed, Shape.general(stories, members));
    SprintPlan exact = CP_SAT.solve(problem);
    SprintPlan greedy = GREEDY.solve(problem);
    assertThat(exact.status()).isIn(SolveStatus.OPTIMAL, SolveStatus.INFEASIBLE);
    assertThat(greedy.status()).isIn(SolveStatus.FEASIBLE, SolveStatus.INFEASIBLE);
    assertThat(exact.status() == SolveStatus.INFEASIBLE)
        .isEqualTo(greedy.status() == SolveStatus.INFEASIBLE);
    assertThat(exact.objective()).isGreaterThanOrEqualTo(greedy.objective());
    Statistics.label("cp-sat status").collect(exact.status());
    Statistics.label("cp-sat beats greedy")
        .collect(exact.objective() > greedy.objective() ? "better" : "equal");
    if (exact.status() == SolveStatus.OPTIMAL) {
      assertThat(exact.bestBound()).isEqualTo(exact.objective());
      assertThat(exact.relativeGap()).isZero();
    }
  }

  @Property(tries = 300)
  void cpSatEqualsDynamicProgramOnSingleCapacity(
      @ForAll long seed, @ForAll @IntRange(min = 0, max = 40) int stories) {
    PlanningProblem problem = RandomBacklogs.generate(seed, Shape.singleCapacity(stories));
    SprintPlan exact = CP_SAT.solve(problem);
    SprintPlan dp = DP.solve(problem);
    assertThat(dp.status()).isEqualTo(exact.status());
    assertThat(dp.objective()).isEqualTo(exact.objective());
    if (dp.status() == SolveStatus.OPTIMAL) {
      assertThat(dp.bestBound()).isEqualTo(dp.objective());
      assertThat(dp.relativeGap()).isZero();
    }
  }

  @Property(tries = 200)
  void cpSatMatchesBruteForceOnTinyProblems(
      @ForAll long seed,
      @ForAll @IntRange(min = 0, max = 10) int stories,
      @ForAll @IntRange(min = 0, max = 3) int members) {
    PlanningProblem problem = RandomBacklogs.generate(seed, Shape.general(stories, members));
    Long optimum = PlanChecks.bruteForceOptimum(problem);
    SprintPlan plan = CP_SAT.solve(problem);
    if (optimum == null) {
      assertThat(plan.status()).isEqualTo(SolveStatus.INFEASIBLE);
    } else {
      assertThat(plan.status()).isEqualTo(SolveStatus.OPTIMAL);
      assertThat(plan.objective()).isEqualTo(optimum);
    }
  }

  @Property(tries = 100)
  void solversAreDeterministic(
      @ForAll long seed,
      @ForAll @IntRange(min = 0, max = 40) int stories,
      @ForAll @IntRange(min = 0, max = 3) int members) {
    PlanningProblem general = RandomBacklogs.generate(seed, Shape.general(stories, members));
    PlanningProblem single = RandomBacklogs.generate(seed, Shape.singleCapacity(stories));
    for (SprintPlanSolver solver : List.of(CP_SAT, GREEDY)) {
      assertSameResult(solver.solve(general), solver.solve(general));
    }
    assertSameResult(DP.solve(single), DP.solve(single));
    assertSameResult(CP_SAT.solve(single), CP_SAT.solve(single));
  }

  private static void assertSameResult(SprintPlan first, SprintPlan second) {
    assertThat(second.status()).isEqualTo(first.status());
    assertThat(second.selectedStoryIds()).isEqualTo(first.selectedStoryIds());
    assertThat(second.explanation()).isEqualTo(first.explanation());
  }

  /** Feasibility plus a consistent explanation. */
  private static void checkPlan(PlanningProblem problem, SprintPlan plan) {
    PlanExplanation explanation = plan.explanation();
    List<String> excludedIds = new ArrayList<>();
    explanation.excluded().forEach(e -> excludedIds.add(e.storyId()));
    List<String> all = new ArrayList<>(plan.selectedStoryIds());
    all.addAll(excludedIds);
    assertThat(all)
        .containsExactlyInAnyOrderElementsOf(
            problem.stories().stream().map(CandidateStory::id).toList());
    if (plan.status() == SolveStatus.INFEASIBLE) {
      assertThat(plan.selectedStoryIds()).isEmpty();
      assertThat(explanation.conflicts()).isNotEmpty();
      return;
    }
    PlanChecks.assertFeasible(problem, plan);
    assertThat(explanation.conflicts()).isEmpty();
    Set<String> binding = new HashSet<>();
    for (ExcludedStory excluded : explanation.excluded()) {
      binding.addAll(excluded.resources());
      boolean capacityReason =
          excluded.reason() == ExclusionReason.CAPACITY
              || excluded.reason() == ExclusionReason.LOWER_VALUE_DENSITY;
      assertThat(excluded.resources().isEmpty()).isEqualTo(!capacityReason);
    }
    for (ResourceUsage usage : explanation.resources()) {
      assertThat(usage.used()).isLessThanOrEqualTo(usage.available());
      assertThat(usage.binding()).isEqualTo(binding.contains(usage.resource()));
    }
    assertThat(explanation.resources()).hasSize(1 + problem.memberCapacities().size());
    if (plan.status() == SolveStatus.OPTIMAL) {
      assertThat(explanation.excluded())
          .extracting(ExcludedStory::reason)
          .doesNotContain(ExclusionReason.NOT_REACHED);
    }
  }
}
