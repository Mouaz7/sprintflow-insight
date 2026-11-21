package com.sprintflowinsight.common;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Gauges for the event outbox (the JDBC event publication registry): how many publications are
 * waiting for their listener and how old the oldest one is. The values are refreshed on a schedule
 * rather than queried on every scrape, so a scrape never waits for the database and a database
 * outage leaves the last known value in place instead of failing the scrape. Every instance reads
 * the same table, so dashboards aggregate these with {@code max}, not {@code sum}.
 */
@Component
public class OutboxMetrics {
  static final String BACKLOG = "sprintflow.outbox.backlog";
  static final String OLDEST_AGE = "sprintflow.outbox.oldest.age";
  private static final Logger LOG = LoggerFactory.getLogger(OutboxMetrics.class);
  private static final String QUERY =
      "select count(*) as backlog, min(publication_date) as oldest from event_publication"
          + " where completion_date is null";

  private final JdbcClient jdbc;
  private final Clock clock;
  private final AtomicLong backlog = new AtomicLong();
  private final AtomicLong oldestAgeSeconds = new AtomicLong();

  @Autowired
  public OutboxMetrics(JdbcClient jdbc, MeterRegistry registry) {
    this(jdbc, registry, Clock.systemUTC());
  }

  OutboxMetrics(JdbcClient jdbc, MeterRegistry registry, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
    Gauge.builder(BACKLOG, backlog, AtomicLong::get)
        .description("Event publications not yet completed by their listener")
        .register(registry);
    Gauge.builder(OLDEST_AGE, oldestAgeSeconds, AtomicLong::get)
        .description("Age of the oldest incomplete event publication, 0 when none")
        .baseUnit("seconds")
        .register(registry);
  }

  @Scheduled(
      initialDelayString = "${app.metrics.outbox-refresh-ms:15000}",
      fixedDelayString = "${app.metrics.outbox-refresh-ms:15000}")
  public void refresh() {
    try {
      jdbc.sql(QUERY)
          .query(
              (row, number) -> {
                backlog.set(row.getLong("backlog"));
                Timestamp oldest = row.getTimestamp("oldest");
                oldestAgeSeconds.set(
                    oldest == null
                        ? 0
                        : Math.max(
                            0, Duration.between(oldest.toInstant(), clock.instant()).toSeconds()));
                return null;
              })
          .list();
    } catch (DataAccessException unavailable) {
      LOG.debug("Outbox metrics not refreshed: {}", unavailable.getClass().getSimpleName());
    }
  }

  long backlog() {
    return backlog.get();
  }

  long oldestAgeSeconds() {
    return oldestAgeSeconds.get();
  }
}
