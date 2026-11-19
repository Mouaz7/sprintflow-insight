package com.sprintflowinsight.workspace.time;

import com.sprintflowinsight.workspace.time.CapacityMath.Load;
import com.sprintflowinsight.workspace.time.CapacityMath.MemberCapacity;
import com.sprintflowinsight.workspace.time.CapacityWarning.Code;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Assembles the capacity view from already loaded rows. Pure, so the whole decision of what counts
 * as overbooked is unit- and mutation-tested without a database.
 */
public final class CapacityBoard {
  private CapacityBoard() {}

  /** The sprint being planned. */
  public record SprintInput(Long id, String name, LocalDate startDate, LocalDate endDate) {}

  /**
   * A member who can carry work (owner, admin or member; viewers never have capacity).
   *
   * @param capacity {@code null} when not configured for this sprint
   */
  public record MemberInput(Long userId, String email, String role, SprintCapacity capacity) {}

  /** Committed work of one assignee, keyed by lower-case email; {@code null} key is unassigned. */
  public record Committed(long minutes, long points, long unestimatedTasks) {
    public static final Committed NONE = new Committed(0, 0, 0);
  }

  public static SprintCapacityResponse build(
      SprintInput sprint,
      List<MemberInput> members,
      Map<String, Committed> committedByEmail,
      Map<Long, Long> loggedByUser) {
    boolean dated = sprint.startDate() != null && sprint.endDate() != null;
    int workingDays = CapacityMath.workingDays(sprint.startDate(), sprint.endDate());
    List<MemberCapacityResponse> rows = new ArrayList<>();
    List<CapacityWarning> warnings = new ArrayList<>();
    if (!dated) warnings.add(new CapacityWarning(Code.SPRINT_DATES_MISSING, null));

    Set<String> memberEmails = new HashSet<>();
    long availableMinutes = 0;
    Integer teamPoints = null;
    for (MemberInput member : members) {
      String email = normalise(member.email());
      memberEmails.add(email);
      Committed committed = committedByEmail.getOrDefault(email, Committed.NONE);
      long logged = loggedByUser.getOrDefault(member.userId(), 0L);
      SprintCapacity settings = member.capacity();
      MemberCapacityResponse row;
      if (settings == null) {
        if (committed.minutes() > 0 || committed.points() > 0 || committed.unestimatedTasks() > 0)
          warnings.add(new CapacityWarning(Code.CAPACITY_NOT_SET, member.userId()));
        row =
            new MemberCapacityResponse(
                member.userId(),
                member.email(),
                member.role(),
                false,
                null,
                null,
                null,
                List.of(),
                null,
                0,
                0,
                null,
                committed.minutes(),
                committed.points(),
                committed.unestimatedTasks(),
                logged,
                null,
                false,
                null,
                false);
      } else {
        int daysOff =
            CapacityMath.workingDaysOff(
                sprint.startDate(), sprint.endDate(), settings.getDaysOff());
        MemberCapacity capacity =
            CapacityMath.memberCapacity(
                workingDays,
                daysOff,
                settings.getMinutesPerDay(),
                settings.getAvailabilityPercent(),
                settings.getPointCapacity());
        Load minutes = CapacityMath.load(committed.minutes(), capacity.availableMinutes());
        Load points =
            capacity.effectivePoints() == null
                ? new Load(null, false)
                : CapacityMath.load(committed.points(), capacity.effectivePoints());
        if (minutes.overbooked())
          warnings.add(new CapacityWarning(Code.MEMBER_OVERBOOKED, member.userId()));
        if (points.overbooked())
          warnings.add(new CapacityWarning(Code.MEMBER_POINTS_OVERBOOKED, member.userId()));
        availableMinutes += capacity.availableMinutes();
        if (capacity.effectivePoints() != null)
          teamPoints = (teamPoints == null ? 0 : teamPoints) + capacity.effectivePoints();
        row =
            new MemberCapacityResponse(
                member.userId(),
                member.email(),
                member.role(),
                true,
                settings.getMinutesPerDay(),
                settings.getAvailabilityPercent(),
                settings.getPointCapacity(),
                settings.getDaysOff(),
                settings.getVersion(),
                capacity.availableDays(),
                capacity.availableMinutes(),
                capacity.effectivePoints(),
                committed.minutes(),
                committed.points(),
                committed.unestimatedTasks(),
                logged,
                minutes.percent(),
                minutes.overbooked(),
                points.percent(),
                points.overbooked());
      }
      if (committed.unestimatedTasks() > 0)
        warnings.add(new CapacityWarning(Code.UNESTIMATED_TASKS, member.userId()));
      rows.add(row);
    }

    long committedMinutes = 0;
    long committedPoints = 0;
    long unassignedMinutes = 0;
    long unassignedPoints = 0;
    long unassignedUnestimated = 0;
    for (Map.Entry<String, Committed> entry : committedByEmail.entrySet()) {
      Committed committed = entry.getValue();
      committedMinutes += committed.minutes();
      committedPoints += committed.points();
      if (entry.getKey() == null || !memberEmails.contains(entry.getKey())) {
        unassignedMinutes += committed.minutes();
        unassignedPoints += committed.points();
        unassignedUnestimated += committed.unestimatedTasks();
      }
    }
    if (unassignedMinutes > 0 || unassignedPoints > 0 || unassignedUnestimated > 0)
      warnings.add(new CapacityWarning(Code.UNASSIGNED_WORK, null));
    if (unassignedUnestimated > 0) warnings.add(new CapacityWarning(Code.UNESTIMATED_TASKS, null));

    Load teamMinutes = CapacityMath.load(committedMinutes, availableMinutes);
    Load teamPointLoad =
        teamPoints == null ? new Load(null, false) : CapacityMath.load(committedPoints, teamPoints);
    if (teamMinutes.overbooked()) warnings.add(new CapacityWarning(Code.TEAM_OVERBOOKED, null));
    if (teamPointLoad.overbooked())
      warnings.add(new CapacityWarning(Code.TEAM_POINTS_OVERBOOKED, null));
    long logged = loggedByUser.values().stream().mapToLong(Long::longValue).sum();
    TeamCapacity team =
        new TeamCapacity(
            availableMinutes,
            teamPoints,
            committedMinutes,
            committedPoints,
            unassignedMinutes,
            unassignedPoints,
            logged,
            teamMinutes.percent(),
            teamMinutes.overbooked(),
            teamPointLoad.percent(),
            teamPointLoad.overbooked());
    return new SprintCapacityResponse(
        sprint.id(),
        sprint.name(),
        sprint.startDate(),
        sprint.endDate(),
        workingDays,
        List.copyOf(rows),
        team,
        List.copyOf(warnings));
  }

  /** Lower-case email used to match task assignees with members; {@code null} stays null. */
  public static String normalise(String email) {
    return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }
}
