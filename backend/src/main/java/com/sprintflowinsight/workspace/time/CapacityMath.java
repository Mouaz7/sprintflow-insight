package com.sprintflowinsight.workspace.time;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Pure capacity arithmetic. No I/O, no clock: every input is explicit so the rules can be checked
 * with properties and mutation tests.
 *
 * <p>Working days are Monday to Friday. Public holidays are not known to the application; members
 * enter them as days off.
 */
public final class CapacityMath {
  /** A running timer never books more than one day; a forgotten timer is capped, not rejected. */
  public static final int MAX_ENTRY_MINUTES = 1440;

  private CapacityMath() {}

  /** Weekdays from {@code start} to {@code end}, both inclusive; 0 when the range is empty. */
  public static int workingDays(LocalDate start, LocalDate end) {
    if (start == null || end == null || end.isBefore(start)) return 0;
    long days = ChronoUnit.DAYS.between(start, end) + 1;
    long weeks = days / 7;
    long working = weeks * 5;
    int remainder = (int) (days % 7);
    DayOfWeek day = start.getDayOfWeek();
    for (int i = 0; i < remainder; i++) {
      if (isWorkingDay(day)) working++;
      day = day.plus(1);
    }
    return (int) Math.min(working, Integer.MAX_VALUE);
  }

  public static boolean isWorkingDay(DayOfWeek day) {
    return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
  }

  /** Distinct days off that fall on a working day inside the sprint; others cost nothing. */
  public static int workingDaysOff(LocalDate start, LocalDate end, Collection<LocalDate> daysOff) {
    if (start == null || end == null || daysOff == null) return 0;
    Set<LocalDate> counted = new HashSet<>();
    for (LocalDate day : daysOff) {
      if (day != null
          && !day.isBefore(start)
          && !day.isAfter(end)
          && isWorkingDay(day.getDayOfWeek())) counted.add(day);
    }
    return counted.size();
  }

  /**
   * Capacity of one member.
   *
   * @param workingDays weekdays in the sprint
   * @param daysOff weekdays in the sprint the member is away; at most {@code workingDays}
   * @param minutesPerDay working minutes on a normal day (0..1440)
   * @param availabilityPercent share of those minutes available to the sprint (0..100)
   * @param pointCapacity points for a full sprint at full availability, or {@code null}
   */
  public static MemberCapacity memberCapacity(
      int workingDays,
      int daysOff,
      int minutesPerDay,
      int availabilityPercent,
      Integer pointCapacity) {
    if (workingDays < 0 || daysOff < 0 || daysOff > workingDays)
      throw new IllegalArgumentException("Days off must be between 0 and the working days");
    if (minutesPerDay < 0 || minutesPerDay > MAX_ENTRY_MINUTES)
      throw new IllegalArgumentException("Minutes per day must be between 0 and 1440");
    if (availabilityPercent < 0 || availabilityPercent > 100)
      throw new IllegalArgumentException("Availability must be between 0 and 100");
    if (pointCapacity != null && pointCapacity < 0)
      throw new IllegalArgumentException("Point capacity must not be negative");
    int availableDays = workingDays - daysOff;
    long minutes = (long) availableDays * minutesPerDay * availabilityPercent / 100;
    Integer points = null;
    if (pointCapacity != null) {
      points =
          workingDays == 0
              ? 0
              : (int)
                  ((long) pointCapacity
                      * availableDays
                      * availabilityPercent
                      / (workingDays * 100L));
    }
    return new MemberCapacity(availableDays, minutes, points);
  }

  /**
   * Load of {@code committed} against {@code available}, as a whole percentage rounded up so that
   * 100.4 % shows as 101 % and is never mistaken for a full-but-fitting sprint.
   *
   * @return {@code null} percent when nothing is available
   */
  public static Load load(long committed, long available) {
    if (committed < 0 || available < 0)
      throw new IllegalArgumentException("Load amounts must not be negative");
    Integer percent =
        available == 0
            ? null
            : java.math.BigDecimal.valueOf(committed)
                .multiply(java.math.BigDecimal.valueOf(100))
                .divide(java.math.BigDecimal.valueOf(available), 0, java.math.RoundingMode.CEILING)
                .min(java.math.BigDecimal.valueOf(Integer.MAX_VALUE))
                .intValue();
    return new Load(percent, committed > available);
  }

  /** Whole minutes for a timer, rounded up, at least 1 and at most {@link #MAX_ENTRY_MINUTES}. */
  public static int timerMinutes(Instant startedAt, Instant stoppedAt) {
    Duration elapsed = Duration.between(startedAt, stoppedAt);
    if (elapsed.isNegative() || elapsed.isZero()) return 1;
    if (elapsed.compareTo(Duration.ofDays(1)) >= 0) return MAX_ENTRY_MINUTES;
    long seconds = elapsed.getSeconds();
    long minutes = seconds / 60 + (seconds % 60 != 0 || elapsed.getNano() > 0 ? 1 : 0);
    return (int) Math.clamp(minutes, 1, MAX_ENTRY_MINUTES);
  }

  /** Whole hours, rounded down: the optimizer must never be told about time that is not there. */
  public static int wholeHours(long minutes) {
    return (int) Math.min(Integer.MAX_VALUE, Math.max(0, minutes) / 60);
  }

  /**
   * Actual against estimate for one task or a total.
   *
   * @return {@code null} fields when there is no estimate; no percentage for a zero estimate
   */
  public static Variance variance(Integer estimateMinutes, long loggedMinutes) {
    if (estimateMinutes == null) return new Variance(null, null);
    long difference = loggedMinutes - estimateMinutes;
    Integer percent =
        estimateMinutes == 0 ? null : (int) Math.round(difference * 100.0 / estimateMinutes);
    return new Variance(difference, percent);
  }

  /**
   * @param minutes logged minus estimated; positive when the work took longer
   * @param percent {@code minutes} relative to the estimate, rounded half up
   */
  public record Variance(Long minutes, Integer percent) {}

  /**
   * @param availableDays working days left after days off
   * @param availableMinutes minutes after days off and availability
   * @param effectivePoints points after days off and availability, or {@code null} when the member
   *     has no point capacity
   */
  public record MemberCapacity(int availableDays, long availableMinutes, Integer effectivePoints) {}

  /**
   * @param percent committed share of the available amount, rounded up; {@code null} when nothing
   *     is available
   * @param overbooked committed strictly exceeds available
   */
  public record Load(Integer percent, boolean overbooked) {}
}
