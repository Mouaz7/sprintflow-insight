package com.sprintflowinsight.workspace.time;

/**
 * A reason to look at the sprint before committing to it.
 *
 * @param userId the member concerned; {@code null} for sprint-wide warnings
 */
public record CapacityWarning(Code code, Long userId) {
  public enum Code {
    SPRINT_DATES_MISSING,
    CAPACITY_NOT_SET,
    MEMBER_OVERBOOKED,
    MEMBER_POINTS_OVERBOOKED,
    TEAM_OVERBOOKED,
    TEAM_POINTS_OVERBOOKED,
    UNESTIMATED_TASKS,
    UNASSIGNED_WORK
  }
}
