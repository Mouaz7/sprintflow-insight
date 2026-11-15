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
 * unselected predecessors it would need (its <em>excluded bundle</em>):
 *
 * <ul>
 *   <li>bundle does not fit: {@code LOWER_VALUE_DENSITY} when its value density is no higher than
 *       that of every <em>chosen bundle</em>, otherwise {@code CAPACITY}. Without any chosen
 *       bundle (nothing was freely chosen, for example because locked-in work fills the sprint)
 *       the reason is always {@code CAPACITY};
 *   <li>bundle fits: {@code NO_ADDED_VALUE} when it is worth nothing, otherwise {@code
 *       NOT_REACHED} (only possible when the solver stopped early).
 * </ul>
 *
 * <p>Chosen bundles mirror excluded ones. Each selected, non-forced story that is not itself a
 * predecessor of another selected, non-forced story forms one chosen bundle together with all its
 * selected, non-forced predecessors (transitively). Forced stories (locked-in stories and their
 * predecessors) never belong to a chosen bundle, a predecessor that was only chosen to enable a
 * successor is judged with that successor, and bundles worth nothing do not count.
 *
 * <p>A resource is binding when it is among the exceeded resources of at least one excluded bundle.
 */
final class PlanExplainer {
  private PlanExplainer() {}

  static PlanExplanation explain(ProblemAnalysis analysis, boolean[] selected) {
    long[] used = analysis.usage(selected);
    double lowestChosenDensity = lowestChosenDensity(analysis, selected);
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

  /**
   * Lowest value density among the chosen bundles (see the class comment), or {@code NaN} when
   * there is none, so that every comparison with it is false.
   */
  static double lowestChosenDensity(ProblemAnalysis analysis, boolean[] selected) {
    boolean[] free = new boolean[analysis.size];
    for (int i = 0; i < analysis.size; i++) {
      free[i] = selected[i] && !analysis.forced[i];
    }
    boolean[] enabler = new boolean[analysis.size];
    for (int i = 0; i < analysis.size; i++) {
      if (free[i]) {
        for (int p : analysis.predecessors[i]) {
          enabler[p] |= free[p];
        }
      }
    }
    double lowest = Double.NaN;
    for (int i = 0; i < analysis.size; i++) {
      if (!free[i] || enabler[i]) {
        continue;
      }
      boolean[] bundle = new boolean[analysis.size];
      collect(analysis, i, free, bundle);
      long value = 0;
      long points = 0;
      for (int b = 0; b < analysis.size; b++) {
        if (bundle[b]) {
          value += analysis.stories.get(b).value();
          points += analysis.stories.get(b).points();
        }
      }
      if (value > 0) {
        double density = ProblemAnalysis.density(value, points);
        lowest = Double.isNaN(lowest) ? density : Math.min(lowest, density);
      }
    }
    return lowest;
  }

  private static void collect(ProblemAnalysis analysis, int story, boolean[] free, boolean[] marks) {
    if (marks[story]) {
      return;
    }
    marks[story] = true;
    for (int p : analysis.predecessors[story]) {
      if (free[p]) {
        collect(analysis, p, free, marks);
      }
    }
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
