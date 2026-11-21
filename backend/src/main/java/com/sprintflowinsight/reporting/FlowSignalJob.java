package com.sprintflowinsight.reporting;

import java.time.Instant;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FlowSignalJob {
  private static final Logger LOG = LoggerFactory.getLogger(FlowSignalJob.class);
  private final JdbcTemplate jdbc;
  private final FlowSignalService signals;

  FlowSignalJob(JdbcTemplate jdbc, FlowSignalService signals) {
    this.jdbc = jdbc;
    this.signals = signals;
  }

  @Scheduled(cron = "${app.flow.signals-cron:0 10 2 * * *}", zone = "UTC")
  @SchedulerLock(name = "daily-flow-signals", lockAtMostFor = "PT1H")
  public void run() {
    LockAssert.assertLocked();
    Instant at = Instant.now();
    long previous = 0;
    while (true) {
      var projects =
          jdbc.queryForList(
              "select id from projects where id>? and trashed_at is null and archived_at is null"
                  + " order by id limit 100",
              Long.class,
              previous);
      if (projects.isEmpty()) return;
      for (long projectId : projects) {
        try {
          signals.checkAndNotify(projectId, at);
        } catch (RuntimeException error) {
          LOG.warn("Could not check flow signals for project {}", projectId, error);
        }
      }
      previous = projects.getLast();
    }
  }
}
