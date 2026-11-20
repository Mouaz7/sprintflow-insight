package com.sprintflowinsight.workspace.time;

/**
 * Sprint totals. Committed amounts include unassigned work and work assigned to people who are no
 * longer members, because the sprint has committed to it either way.
 *
 * @param effectivePoints {@code null} when no member has a point capacity
 */
public record TeamCapacity(
    long availableMinutes,
    Integer effectivePoints,
    long committedMinutes,
    long committedPoints,
    long unassignedMinutes,
    long unassignedPoints,
    long loggedMinutes,
    Integer loadPercent,
    boolean overbooked,
    Integer pointsLoadPercent,
    boolean pointsOverbooked) {}
