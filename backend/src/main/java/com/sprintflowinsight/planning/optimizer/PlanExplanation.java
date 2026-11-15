package com.sprintflowinsight.planning.optimizer;

import java.util.List;

/**
 * Why the plan looks the way it does.
 *
 * @param resources capacity used and available for the team total and for each member
 * @param excluded every candidate that is not in the plan, in backlog order, with its reason
 * @param conflicts why no plan exists; empty unless the plan is {@link SolveStatus#INFEASIBLE}
 */
public record PlanExplanation(
    List<ResourceUsage> resources, List<ExcludedStory> excluded, List<PlanConflict> conflicts) {

  public PlanExplanation {
    resources = List.copyOf(resources);
    excluded = List.copyOf(excluded);
    conflicts = List.copyOf(conflicts);
  }

  /** Resources that stop at least one more story from fitting. */
  public List<ResourceUsage> bindingResources() {
    return resources.stream().filter(ResourceUsage::binding).toList();
  }

  /**
   * Usage of one capacity.
   *
   * @param resource {@value ProblemAnalysis#TOTAL} or {@code member:<id>}
   * @param binding true when some excluded story would fit if this resource had more room
   */
  public record ResourceUsage(String resource, long used, long available, boolean binding) {
    public long slack() {
      return available - used;
    }
  }

  /**
   * One story left out of the plan.
   *
   * @param relatedStoryIds for {@link ExclusionReason#MISSING_DEPENDENCY} the predecessors that
   *     block it; for capacity reasons the unselected predecessors that would have to come along
   * @param resources capacities that the story (with its unselected predecessors) would exceed
   */
  public record ExcludedStory(
      String storyId, ExclusionReason reason, List<String> relatedStoryIds, List<String> resources) {
    public ExcludedStory {
      relatedStoryIds = List.copyOf(relatedStoryIds);
      resources = List.copyOf(resources);
    }
  }

  /** Why a story is not in the plan. */
  public enum ExclusionReason {
    /** Locked out by the planner. */
    LOCKED_OUT,
    /** A predecessor is neither done nor selectable (unknown, locked out or itself blocked). */
    MISSING_DEPENDENCY,
    /**
     * Does not fit in the remaining capacity, although its value density is higher than that of
     * some freely chosen bundle (or nothing was freely chosen): swapping would not raise the total
     * value. Bundles are defined in {@code PlanExplainer}.
     */
    CAPACITY,
    /**
     * Does not fit, and its value per point (with the predecessors it needs) is no higher than that
     * of any freely chosen bundle.
     */
    LOWER_VALUE_DENSITY,
    /** Would fit but adds no business value. */
    NO_ADDED_VALUE,
    /** Would fit and add value, but the solver stopped (time limit) before choosing it. */
    NOT_REACHED,
    /** No plan exists; see {@link PlanExplanation#conflicts()}. */
    PLAN_INFEASIBLE
  }

  /**
   * A reason why no plan exists.
   *
   * @param storyIds the locked-in stories (and forced predecessors) involved
   * @param relatedStoryIds blocking predecessors for {@link ConflictKind#LOCKED_IN_BLOCKED}
   * @param resource the exceeded capacity for {@link ConflictKind#LOCKED_IN_OVER_CAPACITY}
   */
  public record PlanConflict(
      ConflictKind kind, List<String> storyIds, List<String> relatedStoryIds, String resource) {
    public PlanConflict {
      storyIds = List.copyOf(storyIds);
      relatedStoryIds = List.copyOf(relatedStoryIds);
    }
  }

  /** Kinds of infeasibility. */
  public enum ConflictKind {
    /** A locked-in story depends on a story that cannot be planned. */
    LOCKED_IN_BLOCKED,
    /** Locked-in stories and their predecessors alone exceed a capacity. */
    LOCKED_IN_OVER_CAPACITY
  }
}
