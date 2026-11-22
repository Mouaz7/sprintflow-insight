package com.sprintflowinsight.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.support.MigrationFixture;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class EventPublicationClaimsIntegrationTest {
  private static final Instant NOW = Instant.parse("2026-11-21T18:00:00Z");
  private static final Instant RECENT_CUTOFF = NOW.minusSeconds(60);
  private static final Instant RETRY_CUTOFF = NOW.minusSeconds(86_400);

  @Test
  void expandsSchemaAndSupportsOldOutboxWriters() throws Exception {
    try (var fixture = new MigrationFixture()) {
      fixture.migrateTo("37");
      var jdbc = new JdbcTemplate(fixture.dataSource());
      UUID beforeUpgrade = writeWithOldSchema(jdbc, NOW.minusSeconds(120), null);

      assertThat(fixture.migrateTo("38").migrationsExecuted).isEqualTo(1);
      UUID oldWriterAfterUpgrade = writeWithOldSchema(jdbc, NOW.minusSeconds(180), null);
      var claims = new EventPublicationClaims(jdbc);
      assertThat(claims.claim(RECENT_CUTOFF, RETRY_CUTOFF))
          .containsExactlyInAnyOrder(beforeUpgrade, oldWriterAfterUpgrade);

      assertThat(
              jdbc.update(
                  "update event_publication set completion_date=? where id=?",
                  Timestamp.from(NOW),
                  beforeUpgrade))
          .isOne();
      assertThat(
              jdbc.queryForObject(
                  "select serialized_event from event_publication where id=?",
                  String.class,
                  beforeUpgrade))
          .isEqualTo("{}");
    }
  }

  @Test
  void skipsLockedRowsAndReclaimsExpiredLeasesWithoutReplayingCompletedRows() throws Exception {
    try (var fixture = new MigrationFixture()) {
      fixture.migrateTo("38");
      var source = fixture.dataSource();
      var jdbc = new JdbcTemplate(source);
      jdbc.setQueryTimeout(5);
      UUID locked = writeWithOldSchema(jdbc, NOW.minusSeconds(360), null);
      UUID available = writeWithOldSchema(jdbc, RETRY_CUTOFF, null);
      UUID expired = writeWithOldSchema(jdbc, RETRY_CUTOFF.minusSeconds(1), null);
      writeWithOldSchema(jdbc, RECENT_CUTOFF, null);
      writeWithOldSchema(jdbc, NOW.minusSeconds(360), NOW);
      var first = new EventPublicationClaims(jdbc);
      var second = new EventPublicationClaims(new JdbcTemplate(source));

      try (var connection = source.getConnection()) {
        connection.setAutoCommit(false);
        try (var statement =
            connection.prepareStatement("select id from event_publication where id=? for update")) {
          statement.setObject(1, locked);
          statement.executeQuery().close();
          assertThat(first.claim(RECENT_CUTOFF, RETRY_CUTOFF)).containsExactly(available);
        } finally {
          connection.rollback();
        }
      }
      assertThat(second.claim(RECENT_CUTOFF, RETRY_CUTOFF)).containsExactly(locked);
      assertThat(first.claim(RECENT_CUTOFF, RETRY_CUTOFF)).isEmpty();
      assertThat(second.claim(RETRY_CUTOFF, null)).containsExactly(expired);

      jdbc.update(
          "update event_publication set retry_claim_until=clock_timestamp()-interval '1 second'"
              + " where id=?",
          available);
      assertThat(first.claim(RECENT_CUTOFF, RETRY_CUTOFF)).containsExactly(available);
    }
  }

  private static UUID writeWithOldSchema(JdbcTemplate jdbc, Instant published, Instant completed) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        insert into event_publication
          (id, listener_id, event_type, serialized_event, publication_date, completion_date)
        values (?, 'test', 'example.Event', '{}', ?, ?)
        """,
        id,
        Timestamp.from(published),
        completed == null ? null : Timestamp.from(completed));
    return id;
  }
}
