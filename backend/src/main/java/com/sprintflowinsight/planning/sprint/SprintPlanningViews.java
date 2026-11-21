package com.sprintflowinsight.planning.sprint;

import com.sprintflowinsight.planning.optimizer.SprintPlan;
import com.sprintflowinsight.workspace.time.CapacityUnit;
import java.util.List;
import java.util.Map;

public final class SprintPlanningViews {
  private SprintPlanningViews() {}

  public enum Eligibility {
    READY,
    MISSING_ESTIMATE,
    HAS_SCHEDULED_TASK
  }

  public record Story(
      long id,
      String title,
      int value,
      int effort,
      Map<Long, Integer> assignedEffort,
      Eligibility eligibility) {
    public Story {
      assignedEffort = Map.copyOf(assignedEffort);
    }
  }

  public record Member(long userId, String email, int configuredCapacity, int reservedEffort) {}

  public record Options(
      long sprintId,
      CapacityUnit unit,
      int configuredCapacity,
      int reservedEffort,
      int unestimatedCommitments,
      List<Member> members,
      List<Story> stories) {
    public Options {
      members = List.copyOf(members);
      stories = List.copyOf(stories);
    }
  }

  public record Proposal(Options options, SprintPlan plan) {}
}
