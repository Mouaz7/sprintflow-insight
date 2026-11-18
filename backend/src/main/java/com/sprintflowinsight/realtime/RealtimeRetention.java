package com.sprintflowinsight.realtime;

import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Hourly removal of replay-log rows older than {@code app.realtime.retention}. */
@Component
public class RealtimeRetention {
  private final RealtimeEventLog log;

  public RealtimeRetention(RealtimeEventLog log) {
    this.log = log;
  }

  @Scheduled(cron = "${app.realtime.prune-cron:0 17 * * * *}")
  public void prune() {
    log.prune(Instant.now());
  }
}
