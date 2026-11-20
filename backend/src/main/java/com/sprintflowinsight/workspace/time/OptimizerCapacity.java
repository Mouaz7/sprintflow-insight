package com.sprintflowinsight.workspace.time;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Sprint capacity in exactly the shape of the C1 optimizer's {@code PlanningProblem}: an integer
 * total and an integer capacity per member. Member keys are account ids as decimal strings, so the
 * effort a story needs from a member must be keyed the same way.
 *
 * <p>Every owner, admin and member is listed, with 0 when capacity is not set, because the
 * optimizer rejects effort from a member it does not know. The total is the sum of the member
 * values, so per-member rounding can never let the total promise more than the members can give.
 */
public record OptimizerCapacity(
    CapacityUnit unit, int totalCapacity, Map<String, Integer> memberCapacities) {

  public OptimizerCapacity {
    memberCapacities = Collections.unmodifiableMap(new TreeMap<>(memberCapacities));
  }

  /** The key a member has in {@link #memberCapacities()}. */
  public static String memberKey(Long userId) {
    return Long.toString(userId);
  }

  public static OptimizerCapacity from(SprintCapacityResponse view, CapacityUnit unit) {
    Map<String, Integer> members = new TreeMap<>();
    long total = 0;
    for (MemberCapacityResponse member : view.members()) {
      int amount =
          switch (unit) {
            case HOURS -> CapacityMath.wholeHours(member.availableMinutes());
            case POINTS -> member.effectivePoints() == null ? 0 : member.effectivePoints();
          };
      members.put(memberKey(member.userId()), amount);
      total += amount;
    }
    return new OptimizerCapacity(unit, (int) Math.min(Integer.MAX_VALUE, total), members);
  }
}
