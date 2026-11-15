package com.sprintflowinsight.planning.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Independent feasibility checks, written without the production analysis code. */
final class PlanChecks {
  private PlanChecks() {}

  static void assertFeasible(PlanningProblem problem, SprintPlan plan) {
    Set<String> chosen = new HashSet<>(plan.selectedStoryIds());
    assertThat(chosen).hasSameSizeAs(plan.selectedStoryIds());
    long points = 0;
    long value = 0;
    Map<String, Long> effort = new HashMap<>();
    Set<String> candidates = new HashSet<>();
    problem.stories().forEach(story -> candidates.add(story.id()));
    for (CandidateStory story : problem.stories()) {
      if (story.lock() == Lock.LOCKED_IN) {
        assertThat(chosen).as("locked in").contains(story.id());
      }
      if (!chosen.contains(story.id())) {
        continue;
      }
      assertThat(story.lock()).as("locked out").isNotEqualTo(Lock.LOCKED_OUT);
      for (String predecessor : story.predecessorIds()) {
        assertThat(problem.doneStoryIds().contains(predecessor) || chosen.contains(predecessor))
            .as("%s needs %s", story.id(), predecessor)
            .isTrue();
      }
      points += story.points();
      value += story.value();
      story.effortByMember().forEach((m, e) -> effort.merge(m, (long) e, Long::sum));
    }
    assertThat(candidates).containsAll(chosen);
    assertThat(points).as("total capacity").isLessThanOrEqualTo(problem.totalCapacity());
    problem
        .memberCapacities()
        .forEach(
            (member, capacity) ->
                assertThat(effort.getOrDefault(member, 0L)).as(member).isLessThanOrEqualTo(capacity));
    assertThat(plan.objective()).isEqualTo(value);
  }

  /** Optimum by enumerating every subset; only for tiny problems. Null when infeasible. */
  static Long bruteForceOptimum(PlanningProblem problem) {
    int n = problem.stories().size();
    Long best = null;
    for (int mask = 0; mask < (1 << n); mask++) {
      Set<String> chosen = new HashSet<>();
      for (int i = 0; i < n; i++) {
        if ((mask & (1 << i)) != 0) {
          chosen.add(problem.stories().get(i).id());
        }
      }
      Long value = valueIfFeasible(problem, chosen);
      if (value != null && (best == null || value > best)) {
        best = value;
      }
    }
    return best;
  }

  private static Long valueIfFeasible(PlanningProblem problem, Set<String> chosen) {
    long points = 0;
    long value = 0;
    Map<String, Long> effort = new HashMap<>();
    for (CandidateStory story : problem.stories()) {
      boolean in = chosen.contains(story.id());
      if (story.lock() == Lock.LOCKED_IN && !in || story.lock() == Lock.LOCKED_OUT && in) {
        return null;
      }
      if (!in) {
        continue;
      }
      for (String predecessor : story.predecessorIds()) {
        if (!problem.doneStoryIds().contains(predecessor) && !chosen.contains(predecessor)) {
          return null;
        }
      }
      points += story.points();
      value += story.value();
      story.effortByMember().forEach((m, e) -> effort.merge(m, (long) e, Long::sum));
    }
    if (points > problem.totalCapacity()) {
      return null;
    }
    for (var entry : problem.memberCapacities().entrySet()) {
      if (effort.getOrDefault(entry.getKey(), 0L) > entry.getValue()) {
        return null;
      }
    }
    return value;
  }
}
