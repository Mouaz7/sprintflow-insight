package com.sprintflowinsight.planning.optimizer;

import com.sprintflowinsight.planning.optimizer.PlanExplanation.ExcludedStory;
import com.sprintflowinsight.planning.optimizer.PlanExplanation.ExclusionReason;
import com.sprintflowinsight.planning.optimizer.PlanExplanation.ResourceUsage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Derives the explanation from a selection. An excluded story is judged together with the
 * unselected predecessors it would need ("bundle"):
 *
 * <ul>
 *   <li>bundle does not fit: {@code CAPACITY} or, when its value density is no higher than that of
 *       every freely chosen story, {@code LOWER_VALUE_DENSITY};
 *   <li>bundle fits: {@code NO_ADDED_VALUE} when it is worth nothing, otherwise {@code
 *       NOT_REACHED} (only possible when the solver stopped early).
 * </ul>
 *
 * A resource is binding when it is among the exceeded resources of at least one excluded bundle.
 */
final class PlanExplainer {
  private PlanExplainer() {}

  static PlanExplanation explain(ProblemAnalysis analysis, boolean[] selected) {
    long[] used = analysis.usage(selected);
    double lowestChosenDensity = Double.POSITIVE_INFINITY;
    for (int i = 0; i < analysis.size; i++) {
      if (selected[i] && !analysis.forced[i]) {
        CandidateStory story = analysis.stories.get(i);
        lowestChosenDensity =
            Math.min(lowestChosenDensity, ProblemAnalysis.density(story.value(), story.points()));
      }
    }
    Set<String> binding = new HashSet<>();
    List<ExcludedStory> excluded = new ArrayList<>();
    for (int i = 0; i < analysis.size; i++) {
      if (selected[i]) {
        continue;
      }
      String id = analysis.stories.get(i).id();
      if (!analysis.selectable(i)) {
        excluded.add(
            new ExcludedStory(id, analysis.blockedReason[i], analysis.blockers.get(i), List.of()));
        continue;
      }
      int[] bundle = analysis.bundle(i, selected);
      List<String> companions = new ArrayList<>();
      for (int b : bundle) {
        if (b != i) {
          companions.add(analysis.stories.get(b).id());
        }
      }
      List<String> exceeded = analysis.exceeded(used, bundle);
      long value = analysis.value(bundle);
      ExclusionReason reason;
      if (!exceeded.isEmpty()) {
        binding.addAll(exceeded);
        double density = ProblemAnalysis.density(value, analysis.points(bundle));
        reason =
            density <= lowestChosenDensity
                ? ExclusionReason.LOWER_VALUE_DENSITY
                : ExclusionReason.CAPACITY;
      } else {
        reason = value == 0 ? ExclusionReason.NO_ADDED_VALUE : ExclusionReason.NOT_REACHED;
      }
      excluded.add(new ExcludedStory(id, reason, companions, exceeded));
    }
    List<ResourceUsage> usages = new ArrayList<>();
    for (int r = 0; r < analysis.resources.size(); r++) {
      String resource = analysis.resources.get(r);
      usages.add(
          new ResourceUsage(resource, used[r], analysis.capacities[r], binding.contains(resource)));
    }
    return new PlanExplanation(usages, excluded, List.of());
  }

  static PlanExplanation explainInfeasible(ProblemAnalysis analysis) {
    List<ExcludedStory> excluded = new ArrayList<>();
    for (int i = 0; i < analysis.size; i++) {
      String id = analysis.stories.get(i).id();
      excluded.add(
          analysis.selectable(i)
              ? new ExcludedStory(id, ExclusionReason.PLAN_INFEASIBLE, List.of(), List.of())
              : new ExcludedStory(
                  id, analysis.blockedReason[i], analysis.blockers.get(i), List.of()));
    }
    long[] forcedUse = analysis.usage(analysis.forced);
    List<ResourceUsage> usages = new ArrayList<>();
    for (int r = 0; r < analysis.resources.size(); r++) {
      usages.add(
          new ResourceUsage(
              analysis.resources.get(r),
              forcedUse[r],
              analysis.capacities[r],
              forcedUse[r] > analysis.capacities[r]));
    }
    return new PlanExplanation(usages, excluded, analysis.conflicts);
  }
}
