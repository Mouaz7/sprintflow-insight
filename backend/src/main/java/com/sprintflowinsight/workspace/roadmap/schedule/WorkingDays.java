package com.sprintflowinsight.workspace.roadmap.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Monday-to-Friday calendar arithmetic. Public holidays are not modelled. */
public final class WorkingDays {
  private WorkingDays() {}

  public static boolean isWorkingDay(LocalDate date) {
    DayOfWeek day = date.getDayOfWeek();
    return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
  }

  /** Working days from {@code from} to {@code to}, both inclusive; 0 when {@code to < from}. */
  public static long between(LocalDate from, LocalDate to) {
    if (to.isBefore(from)) return 0;
    long days = ChronoUnit.DAYS.between(from, to) + 1;
    long weeks = days / 7;
    long count = weeks * 5;
    LocalDate cursor = from.plusDays(weeks * 7);
    while (!cursor.isAfter(to)) {
      if (isWorkingDay(cursor)) count++;
      cursor = cursor.plusDays(1);
    }
    return count;
  }

  /**
   * The day on which work of {@code days} working days that starts on {@code start} finishes: the
   * {@code ceil(days)}-th working day counting {@code start} itself. Non-positive work finishes on
   * {@code start}. Satisfies {@code between(start, finish(start, n)) == n} for whole {@code n >=
   * 1}.
   */
  public static LocalDate finish(LocalDate start, double days) {
    long needed = (long) Math.ceil(days - 1e-9);
    if (needed <= 0) return start;
    LocalDate cursor = start;
    while (!isWorkingDay(cursor)) cursor = cursor.plusDays(1);
    long weeks = (needed - 1) / 5;
    cursor = cursor.plusWeeks(weeks);
    long remaining = needed - weeks * 5 - 1;
    while (remaining > 0) {
      cursor = cursor.plusDays(1);
      if (isWorkingDay(cursor)) remaining--;
    }
    return cursor;
  }
}
