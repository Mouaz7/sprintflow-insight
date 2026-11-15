package com.sprintflowinsight.realtime;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sequence allocation and the bounded replay log (Flyway V17). Retention keeps at most {@code
 * app.realtime.max-events-per-project} rows per project and nothing older than {@code
 * app.realtime.retention}; the sequence counter itself is never reset.
 */
@Repository
public class RealtimeEventLog {
  /** Most events one gap request returns; a larger gap asks the client to reload instead. */
  static final int PAGE_SIZE = 200;

  // The insert-select yields no row once the project is deleted, so late events are dropped.
  private static final String NEXT_SEQ =
      "insert into realtime_sequence(project_id,last_seq) select ?,1"
          + " where exists (select 1 from projects where id=?)"
          + " on conflict (project_id) do update set last_seq = realtime_sequence.last_seq + 1"
          + " returning last_seq";

  private final JdbcTemplate jdbc;
  private final int maxPerProject;
  private final Duration retention;

  public RealtimeEventLog(
      JdbcTemplate jdbc,
      @Value("${app.realtime.max-events-per-project:1000}") int maxPerProject,
      @Value("${app.realtime.retention:P7D}") Duration retention) {
    if (maxPerProject < PAGE_SIZE)
      throw new IllegalArgumentException(
          "app.realtime.max-events-per-project must be at least " + PAGE_SIZE + ".");
    if (retention.isNegative() || retention.isZero())
      throw new IllegalArgumentException("app.realtime.retention must be positive.");
    this.jdbc = jdbc;
    this.maxPerProject = maxPerProject;
    this.retention = retention;
  }

  /**
   * Assigns the next sequence number and records the event, in the caller's transaction. Returns
   * empty when this source event was already recorded (a replay) or the project no longer exists.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<RealtimeMessage> append(
      UUID sourceEventId,
      long projectId,
      String type,
      Long entityId,
      Long version,
      Instant occurredAt) {
    Boolean seen =
        jdbc.queryForObject(
            "select exists (select 1 from realtime_event where source_event_id=?)",
            Boolean.class,
            sourceEventId);
    if (Boolean.TRUE.equals(seen)) return Optional.empty();
    List<Long> allocated = jdbc.queryForList(NEXT_SEQ, Long.class, projectId, projectId);
    if (allocated.isEmpty()) return Optional.empty();
    long seq = allocated.getFirst();
    jdbc.update(
        "insert into realtime_event"
            + "(project_id,seq,source_event_id,type,entity_id,entity_version,occurred_at)"
            + " values (?,?,?,?,?,?,?)",
        projectId,
        seq,
        sourceEventId,
        type,
        entityId,
        version,
        Timestamp.from(occurredAt));
    if (seq > maxPerProject)
      jdbc.update(
          "delete from realtime_event where project_id=? and seq<=?", projectId, seq - maxPerProject);
    return Optional.of(new RealtimeMessage(projectId, seq, type, entityId, version));
  }

  /** Events after {@code after}, oldest first; see {@link RealtimeEventPage} for {@code resync}. */
  @Transactional(readOnly = true)
  public RealtimeEventPage since(long projectId, long after) {
    long latest =
        jdbc.queryForList(
                "select last_seq from realtime_sequence where project_id=?", Long.class, projectId)
            .stream()
            .findFirst()
            .orElse(0L);
    if (after == latest) return new RealtimeEventPage(projectId, latest, false, List.of());
    if (after < 0 || after > latest || latest - after > PAGE_SIZE)
      return new RealtimeEventPage(projectId, latest, true, List.of());
    List<RealtimeMessage> events =
        jdbc.query(
            "select seq,type,entity_id,entity_version from realtime_event"
                + " where project_id=? and seq>? and seq<=? order by seq",
            (row, index) ->
                new RealtimeMessage(
                    projectId,
                    row.getLong("seq"),
                    row.getString("type"),
                    row.getObject("entity_id", Long.class),
                    row.getObject("entity_version", Long.class)),
            projectId,
            after,
            latest);
    // Rows are contiguous up to the latest number; a shorter list means retention removed some.
    if (events.size() != latest - after)
      return new RealtimeEventPage(projectId, latest, true, List.of());
    return new RealtimeEventPage(projectId, latest, false, events);
  }

  /** Removes events older than the retention window. Returns the number of rows removed. */
  @Transactional
  public int prune(Instant now) {
    return jdbc.update(
        "delete from realtime_event where occurred_at < ?", Timestamp.from(now.minus(retention)));
  }
}
