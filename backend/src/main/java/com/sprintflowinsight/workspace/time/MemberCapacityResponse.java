package com.sprintflowinsight.workspace.time;

import java.time.LocalDate;
import java.util.List;

/**
 * One member's row in the capacity view. Settings are {@code null} and derived amounts zero when
 * {@code configured} is false.
 *
 * @param loadPercent committed estimate minutes against available minutes, rounded up
 * @param pointsLoadPercent committed story points against effective points, rounded up
 */
public record MemberCapacityResponse(
    Long userId,
    String email,
    String role,
    boolean configured,
    Integer minutesPerDay,
    Integer availabilityPercent,
    Integer pointCapacity,
    List<LocalDate> daysOff,
    Long version,
    int availableDays,
    long availableMinutes,
    Integer effectivePoints,
    long committedMinutes,
    long committedPoints,
    long unestimatedTasks,
    long loggedMinutes,
    Integer loadPercent,
    boolean overbooked,
    Integer pointsLoadPercent,
    boolean pointsOverbooked) {}
