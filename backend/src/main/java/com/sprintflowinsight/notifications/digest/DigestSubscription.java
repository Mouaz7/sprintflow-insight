package com.sprintflowinsight.notifications.digest;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "digest_subscription")
public class DigestSubscription {
  @Id private Long userId;

  @Enumerated(EnumType.STRING)
  private DigestFrequency frequency;

  private String timeZone;
  private Instant nextDueAt;
  private Instant checkpointAt;
  private Instant lastSentAt;
  private UUID tokenVersion;
  @Version private Long version;

  protected DigestSubscription() {}

  DigestSubscription(Long userId) {
    this.userId = userId;
    this.frequency = DigestFrequency.OFF;
    this.timeZone = "UTC";
    this.tokenVersion = UUID.randomUUID();
  }

  public Long getUserId() {
    return userId;
  }

  public DigestFrequency getFrequency() {
    return frequency;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public Instant getNextDueAt() {
    return nextDueAt;
  }

  public Instant getCheckpointAt() {
    return checkpointAt;
  }

  public Instant getLastSentAt() {
    return lastSentAt;
  }

  public UUID getTokenVersion() {
    return tokenVersion;
  }

  public Long getVersion() {
    return version;
  }

  public void configure(DigestFrequency next, String zone, Instant now) {
    if (frequency == DigestFrequency.OFF && next != DigestFrequency.OFF) {
      tokenVersion = UUID.randomUUID();
      checkpointAt = now.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
    frequency = next;
    timeZone = zone;
    nextDueAt = DigestTiming.next(now, next, DigestTiming.zone(zone));
  }

  public void completed(Instant cutoff, boolean sent) {
    checkpointAt = cutoff.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    if (sent) lastSentAt = checkpointAt;
    nextDueAt = DigestTiming.next(cutoff, frequency, DigestTiming.zone(timeZone));
  }

  public void retryAfter(Instant now) {
    nextDueAt = now.plusSeconds(900);
  }
}
