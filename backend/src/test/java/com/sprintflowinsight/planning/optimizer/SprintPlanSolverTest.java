package com.sprintflowinsight.planning.optimizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.sprintflowinsight.planning.optimizer.PlanExplanation.ConflictKind;
import com.sprintflowinsight.planning.optimizer.PlanExplanation.ExcludedStory;
import com.sprintflowinsight.planning.optimizer.PlanExplanation.ExclusionReason;
import com.sprintflowinsight.planning.optimizer.PlanExplanation.PlanConflict;
import com.sprintflowinsight.planning.optimizer.PlanExplanation.ResourceUsage;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SprintPlanSolverTest {
  private static final CpSatSprintPlanSolver CP_SAT = new CpSatSprintPlanSolver();
  private static final GreedySprintPlanSolver GREEDY = new GreedySprintPlanSolver();
  private static final DynamicProgrammingSprintPlanSolver DP =
      new DynamicProgrammingSprintPlanSolver();

  /** Equal densities: greedy takes the most valuable story first and blocks the better pair. */
  private static final PlanningProblem GREEDY_TRAP =
      PlanningProblem.of(
          10,
          List.of(
              CandidateStory.of("a", 12, 6),
              CandidateStory.of("b", 10, 5),
              CandidateStory.of("c", 10, 5)));

  @Test
  void solversIdentifyThemselves() {
    assertThat(CP_SAT.kind()).isEqualTo(SolverKind.CP_SAT);
    assertThat(GREEDY.kind()).isEqualTo(SolverKind.GREEDY);
    assertThat(DP.kind()).isEqualTo(SolverKind.DYNAMIC_PROGRAMMING);
    assertThat(GREEDY.supports(GREEDY_TRAP)).isTrue();
    assertThat(CP_SAT.settings()).isEqualTo(CpSatSettings.defaults());
  }

  @Test
  void exactSolversBeatTheGreedyTrap() {
    SprintPlan greedy = GREEDY.solve(GREEDY_TRAP);
    assertThat(greedy.selectedStoryIds()).containsExactly("a");
    assertThat(greedy.objective()).isEqualTo(12);
    assertThat(greedy.status()).isEqualTo(SolveStatus.FEASIBLE);
    assertThat(greedy.bestBound()).isNull();
    assertThat(greedy.relativeGap()).isNull();
    assertThat(greedy.solver()).isEqualTo(SolverKind.GREEDY);

    for (SprintPlanSolver exact : List.of(CP_SAT, DP)) {
      SprintPlan plan = exact.solve(GREEDY_TRAP);
      assertThat(plan.selectedStoryIds()).containsExactly("b", "c");
      assertThat(plan.objective()).isEqualTo(20);
      assertThat(plan.status()).isEqualTo(SolveStatus.OPTIMAL);
      assertThat(plan.bestBound()).isEqualTo(20);
      assertThat(plan.relativeGap()).isZero();
      assertThat(plan.solver()).isEqualTo(exact.kind());
      assertThat(plan.wallTime().isNegative()).isFalse();
      assertThat(plan.explanation().excluded())
          .containsExactly(
              new ExcludedStory("a", ExclusionReason.LOWER_VALUE_DENSITY, List.of(), List.of("total")));
      assertThat(plan.explanation().resources())
          .containsExactly(new ResourceUsage("total", 10, 10, true));
    }
  }

  @Test
  void greedyBreaksDensityTiesByValueThenBacklogOrder() {
    var problem =
        PlanningProblem.of(
            4,
            List.of(
                CandidateStory.of("low", 2, 1),
                CandidateStory.of("first", 4, 2),
                CandidateStory.of("second", 4, 2),
                CandidateStory.of("best", 9, 3)));
    // best (3.0) first, then 1 point left: low (2.0) fits, the 2-point stories do not.
    assertThat(GREEDY.solve(problem).selectedStoryIds()).containsExactly("low", "best");
    var ties =
        PlanningProblem.of(
            2, List.of(CandidateStory.of("first", 4, 2), CandidateStory.of("second", 4, 2)));
    assertThat(GREEDY.solve(ties).selectedStoryIds()).containsExactly("first");
    var equalDensity =
        PlanningProblem.of(
            3, List.of(CandidateStory.of("small", 2, 1), CandidateStory.of("large", 6, 3)));
    assertThat(GREEDY.solve(equalDensity).selectedStoryIds()).containsExactly("large");
  }

  @Test
  void greedyJudgesAStoryTogetherWithThePredecessorsItNeeds() {
    var problem =
        PlanningProblem.of(
            3,
            List.of(
                CandidateStory.of("enabler", 0, 1),
                CandidateStory.of("feature", 10, 1).withPredecessors("enabler"),
                CandidateStory.of("other", 4, 1),
                CandidateStory.of("free", 0, 0)));
    SprintPlan plan = GREEDY.solve(problem);
    assertThat(plan.selectedStoryIds()).containsExactly("enabler", "feature", "other");
    assertThat(plan.explanation().excluded())
        .containsExactly(
            new ExcludedStory("free", ExclusionReason.NO_ADDED_VALUE, List.of(), List.of()));
    assertThat(plan.explanation().bindingResources()).isEmpty();
    assertThat(plan.explanation().resources().getFirst().slack()).isZero();
    assertThat(new ResourceUsage("total", 3, 5, false).slack()).isEqualTo(2);
  }

  @Test
  void zeroPointStoriesWithValueAreAlwaysWorthTaking() {
    var problem =
        PlanningProblem.of(
            1, List.of(CandidateStory.of("dense", 5, 1), CandidateStory.of("gift", 1, 0)));
    assertThat(GREEDY.solve(problem).selectedStoryIds()).containsExactly("dense", "gift");
    assertThat(ProblemAnalysis.density(1, 0)).isInfinite();
    assertThat(ProblemAnalysis.density(0, 0)).isZero();
    assertThat(ProblemAnalysis.density(3, 2)).isEqualTo(1.5);
  }

  @Test
  void excludedStoriesExplainCapacityAgainstLowerDensity() {
    var problem =
        PlanningProblem.of(
            10,
            List.of(
                CandidateStory.of("a", 11, 5),
                CandidateStory.of("b", 6, 5),
                CandidateStory.of("c", 10, 6),
                CandidateStory.of("d", 1, 6)));
    SprintPlan plan = CP_SAT.solve(problem);
    assertThat(plan.selectedStoryIds()).containsExactly("a", "b");
    assertThat(plan.explanation().excluded())
        .containsExactly(
            new ExcludedStory("c", ExclusionReason.CAPACITY, List.of(), List.of("total")),
            new ExcludedStory("d", ExclusionReason.LOWER_VALUE_DENSITY, List.of(), List.of("total")));
  }

  @Test
  void locksAndMissingDependenciesBlockStories() {
    var problem =
        new PlanningProblem(
            List.of(
                CandidateStory.of("out", 50, 1).withLock(Lock.LOCKED_OUT),
                CandidateStory.of("needs-out", 50, 1).withPredecessors("out"),
                CandidateStory.of("needs-chain", 50, 1).withPredecessors("needs-out", "shipped"),
                CandidateStory.of("needs-unknown", 50, 1).withPredecessors("ghost", "shipped"),
                CandidateStory.of("needs-shipped", 5, 1).withPredecessors("shipped"),
                CandidateStory.of("must", 1, 1).withLock(Lock.LOCKED_IN)),
            Set.of("shipped"),
            10,
            Map.of());
    for (SprintPlanSolver solver : List.of(CP_SAT, GREEDY, DP)) {
      SprintPlan plan = solver.solve(problem);
      assertThat(plan.selectedStoryIds()).containsExactly("needs-shipped", "must");
      assertThat(plan.explanation().excluded())
          .containsExactly(
              new ExcludedStory("out", ExclusionReason.LOCKED_OUT, List.of(), List.of()),
              new ExcludedStory(
                  "needs-out", ExclusionReason.MISSING_DEPENDENCY, List.of("out"), List.of()),
              new ExcludedStory(
                  "needs-chain",
                  ExclusionReason.MISSING_DEPENDENCY,
                  List.of("needs-out"),
                  List.of()),
              new ExcludedStory(
                  "needs-unknown",
                  ExclusionReason.MISSING_DEPENDENCY,
                  List.of("ghost"),
                  List.of()));
    }
  }

  @Test
  void lockedInStoriesBringTheirPredecessorsAndDoNotSetTheDensityBar() {
    var problem =
        PlanningProblem.of(
            6,
            List.of(
                CandidateStory.of("base", 0, 2),
                CandidateStory.of("locked", 1, 2).withPredecessors("base").withLock(Lock.LOCKED_IN),
                CandidateStory.of("good", 9, 1),
                CandidateStory.of("fair", 4, 2)));
    for (SprintPlanSolver solver : List.of(CP_SAT, GREEDY, DP)) {
      SprintPlan plan = solver.solve(problem);
      assertThat(plan.selectedStoryIds()).containsExactly("base", "locked", "good");
      // fair (2.0) would need 2 points, only 1 is left; the only free choice is good (9.0).
      assertThat(plan.explanation().excluded())
          .containsExactly(
              new ExcludedStory(
                  "fair", ExclusionReason.LOWER_VALUE_DENSITY, List.of(), List.of("total")));
    }
  }

  @Test
  void memberCapacityIsReportedAsBinding() {
    var problem =
        new PlanningProblem(
            List.of(
                CandidateStory.of("x", 10, 3).withEffort(Map.of("al", 3)),
                CandidateStory.of("y", 9, 3).withEffort(Map.of("al", 3)),
                CandidateStory.of("z", 2, 3).withEffort(Map.of("bo", 3)),
                CandidateStory.of("w", 30, 3)
                    .withEffort(Map.of("bo", 2))
                    .withPredecessors("y")),
            Set.of(),
            100,
            Map.of("al", 4, "bo", 5));
    assertThat(DP.supports(problem)).isFalse();
    for (SprintPlanSolver solver : List.of(CP_SAT, GREEDY)) {
      SprintPlan plan = solver.solve(problem);
      // y unlocks w; z still fits bo's remaining effort; x competes with y for al.
      assertThat(plan.selectedStoryIds()).containsExactly("y", "z", "w");
      assertThat(plan.objective()).isEqualTo(41);
      assertThat(plan.explanation().resources())
          .containsExactly(
              new ResourceUsage("total", 9, 100, false),
              new ResourceUsage("member:al", 3, 4, true),
              new ResourceUsage("member:bo", 5, 5, false));
      assertThat(plan.explanation().excluded())
          .containsExactly(
              new ExcludedStory("x", ExclusionReason.CAPACITY, List.of(), List.of("member:al")));
    }
  }

  @Test
  void capacityExclusionsNameTheCompanionPredecessors() {
    var problem =
        PlanningProblem.of(
            2,
            List.of(
                CandidateStory.of("quick", 5, 2),
                CandidateStory.of("setup", 1, 1),
                CandidateStory.of("payoff", 20, 2).withPredecessors("setup")));
    SprintPlan plan = CP_SAT.solve(problem);
    assertThat(plan.selectedStoryIds()).containsExactly("quick");
    assertThat(plan.explanation().excluded())
        .containsExactly(
            new ExcludedStory("setup", ExclusionReason.LOWER_VALUE_DENSITY, List.of(), List.of("total")),
            new ExcludedStory(
                "payoff", ExclusionReason.CAPACITY, List.of("setup"), List.of("total")));
  }

  @Test
  void unfinishedSearchesReportStoriesThatWouldStillFit() {
    var analysis =
        new ProblemAnalysis(
            PlanningProblem.of(
                5, List.of(CandidateStory.of("a", 3, 1), CandidateStory.of("b", 0, 1))));
    SprintPlan plan =
        analysis.plan(SolverKind.CP_SAT, SolveStatus.FEASIBLE, new boolean[2], 4L, Duration.ZERO);
    assertThat(plan.objective()).isZero();
    assertThat(plan.relativeGap()).isEqualTo(1.0);
    assertThat(plan.explanation().excluded())
        .extracting(ExcludedStory::reason)
        .containsExactly(ExclusionReason.NOT_REACHED, ExclusionReason.NO_ADDED_VALUE);
  }

  @Test
  void lockedInStoryWithBlockedDependencyMakesThePlanInfeasible() {
    var problem =
        PlanningProblem.of(
            10,
            List.of(
                CandidateStory.of("out", 1, 1).withLock(Lock.LOCKED_OUT),
                CandidateStory.of("must", 1, 1).withPredecessors("out").withLock(Lock.LOCKED_IN),
                CandidateStory.of("free", 1, 1)));
    for (SprintPlanSolver solver : List.of(CP_SAT, GREEDY, DP)) {
      SprintPlan plan = solver.solve(problem);
      assertThat(plan.status()).isEqualTo(SolveStatus.INFEASIBLE);
      assertThat(plan.selectedStoryIds()).isEmpty();
      assertThat(plan.objective()).isZero();
      assertThat(plan.bestBound()).isNull();
      assertThat(plan.relativeGap()).isNull();
      assertThat(plan.explanation().conflicts())
          .containsExactly(
              new PlanConflict(
                  ConflictKind.LOCKED_IN_BLOCKED, List.of("must"), List.of("out"), null));
      assertThat(plan.explanation().excluded())
          .containsExactly(
              new ExcludedStory("out", ExclusionReason.LOCKED_OUT, List.of(), List.of()),
              new ExcludedStory(
                  "must", ExclusionReason.MISSING_DEPENDENCY, List.of("out"), List.of()),
              new ExcludedStory("free", ExclusionReason.PLAN_INFEASIBLE, List.of(), List.of()));
    }
  }

  @Test
  void lockedInStoriesOverCapacityMakeThePlanInfeasible() {
    var problem =
        new PlanningProblem(
            List.of(
                CandidateStory.of("base", 1, 2).withEffort(Map.of("al", 1)),
                CandidateStory.of("x", 1, 2)
                    .withPredecessors("base")
                    .withLock(Lock.LOCKED_IN)
                    .withEffort(Map.of("bo", 1)),
                CandidateStory.of("y", 1, 0).withLock(Lock.LOCKED_IN)),
            Set.of(),
            3,
            Map.of("al", 1, "bo", 0));
    for (SprintPlanSolver solver : List.of(CP_SAT, GREEDY)) {
      SprintPlan plan = solver.solve(problem);
      assertThat(plan.status()).isEqualTo(SolveStatus.INFEASIBLE);
      assertThat(plan.explanation().conflicts())
          .containsExactly(
              new PlanConflict(
                  ConflictKind.LOCKED_IN_OVER_CAPACITY, List.of("base", "x"), List.of(), "total"),
              new PlanConflict(
                  ConflictKind.LOCKED_IN_OVER_CAPACITY, List.of("x"), List.of(), "member:bo"));
      assertThat(plan.explanation().resources())
          .containsExactly(
              new ResourceUsage("total", 4, 3, true),
              new ResourceUsage("member:al", 1, 1, false),
              new ResourceUsage("member:bo", 1, 0, true));
      assertThat(plan.explanation().bindingResources()).hasSize(2);
    }
    var single = PlanningProblem.of(1, List.of(CandidateStory.of("x", 1, 2).withLock(Lock.LOCKED_IN)));
    assertThat(DP.supports(single)).isTrue();
    assertThat(DP.solve(single).status()).isEqualTo(SolveStatus.INFEASIBLE);
  }

  @Test
  void dynamicProgramSolvesPrecedenceTrees() {
    var problem =
        PlanningProblem.of(
            6,
            List.of(
                CandidateStory.of("root", 1, 2),
                CandidateStory.of("left", 2, 2).withPredecessors("root"),
                CandidateStory.of("deep", 20, 2).withPredecessors("left"),
                CandidateStory.of("right", 3, 1).withPredecessors("root"),
                CandidateStory.of("solo", 4, 2)));
    SprintPlan plan = DP.solve(problem);
    assertThat(plan.selectedStoryIds()).containsExactly("root", "left", "deep");
    assertThat(plan.objective()).isEqualTo(23);
    assertThat(CP_SAT.solve(problem).objective()).isEqualTo(23);
  }

  @Test
  void dynamicProgramPrefersLeavingOutStoriesWithoutValue() {
    var problem =
        PlanningProblem.of(4, List.of(CandidateStory.of("zero", 0, 0), CandidateStory.of("v", 5, 4)));
    assertThat(DP.solve(problem).selectedStoryIds()).containsExactly("v");
  }

  @Test
  void dynamicProgramReportsItsLimits() {
    var twoParents =
        PlanningProblem.of(
            5,
            List.of(
                CandidateStory.of("a", 1, 1),
                CandidateStory.of("b", 1, 1),
                CandidateStory.of("c", 1, 1).withPredecessors("a", "b")));
    assertThat(DP.supports(twoParents)).isFalse();
    assertThatIllegalArgumentException().isThrownBy(() -> DP.solve(twoParents));

    var oneParentLocked =
        PlanningProblem.of(
            5,
            List.of(
                CandidateStory.of("a", 1, 1).withLock(Lock.LOCKED_IN),
                CandidateStory.of("b", 1, 1),
                CandidateStory.of("c", 1, 1).withPredecessors("a", "b")));
    assertThat(DP.supports(oneParentLocked)).isTrue();
    assertThat(DP.solve(oneParentLocked).selectedStoryIds()).containsExactly("a", "b", "c");

    var members =
        new PlanningProblem(List.of(CandidateStory.of("a", 1, 1)), Set.of(), 5, Map.of("al", 1));
    assertThat(DP.supports(members)).isFalse();
    assertThatIllegalArgumentException().isThrownBy(() -> DP.solve(members));

    var cells = (int) DynamicProgrammingSprintPlanSolver.MAX_TABLE_CELLS / 2;
    var atLimit = PlanningProblem.of(cells - 1, List.of(CandidateStory.of("a", 1, 1)));
    assertThat(DP.supports(atLimit)).isTrue();
    var overLimit = PlanningProblem.of(cells, List.of(CandidateStory.of("a", 1, 1)));
    assertThat(DP.supports(overLimit)).isFalse();
    assertThatIllegalArgumentException().isThrownBy(() -> DP.solve(overLimit));
  }

  @Test
  void emptyBacklogGivesAnEmptyOptimalPlan() {
    var empty = PlanningProblem.of(5, List.of());
    for (SprintPlanSolver solver : List.of(CP_SAT, DP)) {
      SprintPlan plan = solver.solve(empty);
      assertThat(plan.status()).isEqualTo(SolveStatus.OPTIMAL);
      assertThat(plan.objective()).isZero();
      assertThat(plan.bestBound()).isZero();
      assertThat(plan.relativeGap()).isZero();
    }
  }

  @Test
  void cpSatBreaksValueTiesTowardsTheTopOfTheBacklog() {
    var problem =
        PlanningProblem.of(
            2,
            List.of(
                CandidateStory.of("top", 5, 1),
                CandidateStory.of("pair-1", 5, 1),
                CandidateStory.of("pair-2", 0, 1),
                CandidateStory.of("bottom", 5, 1)));
    assertThat(CP_SAT.solve(problem).selectedStoryIds()).containsExactly("top", "pair-1");
    assertThat(CpSatSprintPlanSolver.tieBreakScale(new ProblemAnalysis(problem))).isEqualTo(11);
  }

  @Test
  void cpSatDropsTheTieBreakWhenValuesAreHuge() {
    List<CandidateStory> stories = new java.util.ArrayList<>();
    for (int i = 0; i < 300; i++) {
      stories.add(CandidateStory.of("s" + i, CandidateStory.MAX_VALUE, 1));
    }
    var huge = PlanningProblem.of(2, stories);
    assertThat(CpSatSprintPlanSolver.tieBreakScale(new ProblemAnalysis(huge))).isOne();
    SprintPlan plan = CP_SAT.solve(huge);
    assertThat(plan.objective()).isEqualTo(2 * CandidateStory.MAX_VALUE);
    assertThat(plan.bestBound()).isEqualTo(2 * CandidateStory.MAX_VALUE);
    // Small problems keep the backlog-order preference.
    var edge = PlanningProblem.of(1, List.of(CandidateStory.of("a", 0, 1)));
    assertThat(CpSatSprintPlanSolver.tieBreakScale(new ProblemAnalysis(edge))).isEqualTo(2);
  }

  @Test
  void cpSatReportsBoundAndGapWhenStoppedEarly() {
    var problem = RandomBacklogs.generate(7, new RandomBacklogs.Shape(400, 6, false, false, true));
    var hurried =
        new CpSatSprintPlanSolver(
            new CpSatSettings(Duration.ofMillis(1), 1, 0, false));
    SprintPlan plan = hurried.solve(problem);
    PlanChecks.assertFeasible(problem, plan);
    // Timing decides whether a first solution was found within 1 ms.
    if (plan.status() == SolveStatus.UNKNOWN) {
      assertThat(plan.selectedStoryIds()).isEqualTo(GREEDY.solve(problem).selectedStoryIds());
      assertThat(plan.bestBound()).isNull();
      assertThat(plan.relativeGap()).isNull();
    } else {
      assertThat(plan.status()).isIn(SolveStatus.OPTIMAL, SolveStatus.FEASIBLE);
      assertThat(plan.bestBound()).isGreaterThanOrEqualTo(plan.objective());
      assertThat(plan.relativeGap())
          .isEqualTo(SprintPlan.gap(plan.objective(), plan.bestBound()));
    }
  }
}
