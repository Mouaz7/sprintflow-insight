package com.sprintflowinsight.notifications.digest;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DigestTimingTest {
  @ParameterizedTest
  @CsvSource({
    "2026-03-28T08:00:00Z,DAILY,Europe/Stockholm,2026-03-29T06:00:00Z",
    "2026-10-24T08:00:00Z,DAILY,Europe/Stockholm,2026-10-25T07:00:00Z",
    "2026-11-19T06:00:00Z,DAILY,Europe/Stockholm,2026-11-19T07:00:00Z",
    "2026-11-19T07:00:00Z,DAILY,Europe/Stockholm,2026-11-20T07:00:00Z",
    "2026-11-23T08:00:00Z,WEEKLY,UTC,2026-11-30T08:00:00Z",
    "2026-11-22T23:00:00Z,WEEKLY,UTC,2026-11-23T08:00:00Z",
    "2026-11-19T12:00:00Z,DAILY,Asia/Tokyo,2026-11-19T23:00:00Z"
  })
  void schedulesByLocalCalendarAcrossDstAndExactBoundaries(
      String now, String frequency, String zone, String expected) {
    assertThat(
            DigestTiming.next(
                Instant.parse(now), DigestFrequency.valueOf(frequency), DigestTiming.zone(zone)))
        .isEqualTo(Instant.parse(expected));
  }

  @Test
  void offHasNoDueInstantAndOnlyValidIanaZonesAreAccepted() {
    assertThat(DigestTiming.next(Instant.now(), DigestFrequency.OFF, ZoneId.of("UTC"))).isNull();
    assertThatThrownBy(() -> DigestTiming.zone("+01:00")).hasMessageContaining("IANA");
    assertThatThrownBy(() -> DigestTiming.zone("Not/AZone")).hasMessageContaining("IANA");
    assertThatThrownBy(() -> DigestTiming.zone(null)).hasMessageContaining("IANA");
  }

  @Test
  void translatesOnlyKnownNotificationPrefixesAndPreservesUserText() {
    assertThat(DigestWorker.localized("Mentioned you in a comment on “Release”.", true))
        .isEqualTo("Du nämndes i en kommentar på “Release”.");
    assertThat(DigestWorker.localized("New comment on “Title”.", true))
        .isEqualTo("Ny kommentar på “Title”.");
    assertThat(DigestWorker.localized("You were assigned task “Title”.", true))
        .isEqualTo("Du har tilldelats uppgiften “Title”.");
    assertThat(DigestWorker.localized("Custom project message", true))
        .isEqualTo("Custom project message");
    assertThat(DigestWorker.localized("Mentioned you in a comment on “Title”.", false))
        .isEqualTo("Mentioned you in a comment on “Title”.");
  }
}
