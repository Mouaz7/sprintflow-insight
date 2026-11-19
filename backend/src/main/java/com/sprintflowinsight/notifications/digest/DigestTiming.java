package com.sprintflowinsight.notifications.digest;

import com.sprintflowinsight.common.error.ApiException;
import java.time.*;
import java.time.temporal.TemporalAdjusters;

public final class DigestTiming {
  private DigestTiming() {}

  public static ZoneId zone(String value) {
    if (value == null || !ZoneId.getAvailableZoneIds().contains(value))
      throw ApiException.badRequest("Choose a valid IANA time zone.");
    return ZoneId.of(value);
  }

  public static Instant next(Instant now, DigestFrequency frequency, ZoneId zone) {
    if (frequency == DigestFrequency.OFF) return null;
    var day = now.atZone(zone).toLocalDate();
    if (frequency == DigestFrequency.WEEKLY)
      day = day.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    var next = day.atTime(8, 0).atZone(zone);
    if (!next.toInstant().isAfter(now))
      next = frequency == DigestFrequency.WEEKLY ? next.plusWeeks(1) : next.plusDays(1);
    return next.toInstant();
  }
}
