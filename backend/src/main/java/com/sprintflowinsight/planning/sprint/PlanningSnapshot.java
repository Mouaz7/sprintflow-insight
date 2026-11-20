package com.sprintflowinsight.planning.sprint;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.planning.optimizer.CandidateStory;
import com.sprintflowinsight.planning.optimizer.Lock;
import com.sprintflowinsight.planning.optimizer.PlanningProblem;
import com.sprintflowinsight.planning.sprint.SprintPlanningViews.Eligibility;
import com.sprintflowinsight.planning.sprint.SprintPlanningViews.Member;
import com.sprintflowinsight.planning.sprint.SprintPlanningViews.Options;
import com.sprintflowinsight.planning.sprint.SprintPlanningViews.Story;
import com.sprintflowinsight.workspace.time.CapacityUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

record PlanningSnapshot(Options options, Map<Long, List<String>> dependencies) {

  PlanningProblem problem(SprintPlanRequest request) {
    if (options.unestimatedCommitments() > 0)
      throw ApiException.conflict(
          "Estimate all unfinished sprint commitments before proposing more work.");
    var in = new HashSet<>(request.lockedIn());
    var out = new HashSet<>(request.lockedOut());
    if (in.size() != request.lockedIn().size()
        || out.size() != request.lockedOut().size()
        || in.stream().anyMatch(out::contains))
      throw ApiException.badRequest("Supply each story lock once and choose one direction.");
    Set<Long> candidateIds =
        options.stories().stream()
            .filter(story -> story.eligibility() == Eligibility.READY)
            .map(Story::id)
            .collect(Collectors.toSet());
    if (!candidateIds.containsAll(in) || !candidateIds.containsAll(out))
      throw ApiException.badRequest(
          "Locks must refer to selectable backlog stories in this project.");

    Set<Long> members = options.members().stream().map(Member::userId).collect(Collectors.toSet());
    if (!members.containsAll(request.memberCapacities().keySet()))
      throw ApiException.badRequest("Capacity can only be adjusted for current working members.");

    var available = new TreeMap<String, Integer>();
    int teamRemaining = 0;
    for (Member member : options.members()) {
      int capacity =
          request.memberCapacities().getOrDefault(member.userId(), member.configuredCapacity());
      if (capacity < member.reservedEffort()) throw overCommitted();
      available.put(Long.toString(member.userId()), capacity - member.reservedEffort());
      teamRemaining += capacity;
    }
    int total =
        request.totalCapacity() == null
            ? teamRemaining
            : Math.min(request.totalCapacity(), teamRemaining);
    if (total < options.reservedEffort()) throw overCommitted();

    var candidates = new ArrayList<CandidateStory>();
    for (Story story : options.stories()) {
      if (!candidateIds.contains(story.id())) continue;
      var effort = new TreeMap<String, Integer>();
      story.assignedEffort().forEach((member, amount) -> effort.put(Long.toString(member), amount));
      Lock lock =
          in.contains(story.id())
              ? Lock.LOCKED_IN
              : out.contains(story.id()) ? Lock.LOCKED_OUT : Lock.NONE;
      candidates.add(
          new CandidateStory(
              Long.toString(story.id()),
              story.value(),
              story.effort(),
              effort,
              dependencies.getOrDefault(story.id(), List.of()),
              lock));
    }
    try {
      return new PlanningProblem(candidates, Set.of(), total - options.reservedEffort(), available);
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest(e.getMessage());
    }
  }

  private static ApiException overCommitted() {
    return ApiException.conflict("Existing sprint commitments exceed the scenario capacity.");
  }

  record Work(
      long id,
      Long storyId,
      Long sprintId,
      String status,
      Long memberId,
      Integer estimatedMinutes,
      int points) {
    boolean done() {
      return "Done".equals(status);
    }
  }

  record Effort(int total, Map<Long, Integer> assigned, boolean complete) {
    static Effort forWork(CapacityUnit unit, List<Work> tasks, int storyPoints) {
      var rawAssigned = new TreeMap<Long, Integer>();
      int unassigned = 0;
      boolean complete = unit == CapacityUnit.POINTS || !tasks.isEmpty();
      for (Work task : tasks) {
        Integer amount =
            unit == CapacityUnit.HOURS ? task.estimatedMinutes() : Integer.valueOf(task.points());
        if (amount == null) {
          complete = false;
          continue;
        }
        if (task.memberId() == null) unassigned += amount;
        else rawAssigned.merge(task.memberId(), amount, Integer::sum);
      }
      int total = rounded(unit, unassigned);
      var assigned = new TreeMap<Long, Integer>();
      for (var item : rawAssigned.entrySet()) {
        int effort = rounded(unit, item.getValue());
        assigned.put(item.getKey(), effort);
        total += effort;
      }
      if (unit == CapacityUnit.POINTS) total = Math.max(total, storyPoints);
      return new Effort(total, assigned, complete);
    }

    private static int rounded(CapacityUnit unit, int amount) {
      return unit == CapacityUnit.HOURS ? Math.ceilDiv(amount, 60) : amount;
    }
  }
}
