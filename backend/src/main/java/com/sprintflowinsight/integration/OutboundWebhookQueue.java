package com.sprintflowinsight.integration;

import com.sprintflowinsight.workspace.events.TaskCreated;
import com.sprintflowinsight.workspace.events.TaskDeleted;
import com.sprintflowinsight.workspace.events.TaskMoved;
import com.sprintflowinsight.workspace.events.TaskUpdated;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
class OutboundWebhookQueue {
  private static final int MAX_ATTEMPTS = 5;
  private static final int[] RETRY_DELAY_SECONDS = {5, 30, 120, 600};

  record Claim(
      UUID id,
      UUID claimToken,
      int attempt,
      String url,
      String encryptedSecret,
      String event,
      String payload) {
    @Override
    public String toString() {
      return "OutboundWebhookClaim[id=" + id + ", attempt=" + attempt + "]";
    }
  }

  record Event(UUID id, String type, long projectId, long taskId, Instant occurredAt) {}

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  OutboundWebhookQueue(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @ApplicationModuleListener
  void taskCreated(TaskCreated event) {
    enqueue(
        new Event(
            event.eventId(),
            "task.created",
            event.projectId(),
            event.taskId(),
            event.occurredAt()));
  }

  @ApplicationModuleListener
  void taskUpdated(TaskUpdated event) {
    enqueue(
        new Event(
            event.eventId(),
            "task.updated",
            event.projectId(),
            event.taskId(),
            event.occurredAt()));
  }

  @ApplicationModuleListener
  void taskMoved(TaskMoved event) {
    enqueue(
        new Event(
            event.eventId(), "task.moved", event.projectId(), event.taskId(), event.occurredAt()));
  }

  @ApplicationModuleListener
  void taskDeleted(TaskDeleted event) {
    enqueue(
        new Event(
            event.eventId(),
            "task.deleted",
            event.projectId(),
            event.taskId(),
            event.occurredAt()));
  }

  private void enqueue(Event event) {
    jdbc.update(
        """
insert into outbound_webhook_delivery(id,webhook_id,event_id,event_type,payload)
select gen_random_uuid(),w.id,?,?,? from outbound_webhook w join projects p on p.id=w.project_id
where w.project_id=? and ?=any(w.event_types) and w.created_at<=?
  and p.archived_at is null and p.trashed_at is null
on conflict(webhook_id,event_id) do nothing
""",
        event.id(),
        event.type(),
        json.writeValueAsString(event),
        event.projectId(),
        event.type(),
        java.sql.Timestamp.from(event.occurredAt()));
  }

  @Transactional
  public Optional<Claim> claim() {
    jdbc.update(
        """
        update outbound_webhook_delivery set outcome='FAILED',claim_token=null
        where outcome='PENDING' and attempts>=? and next_attempt_at<=clock_timestamp()
        """,
        MAX_ATTEMPTS);
    UUID claimToken = UUID.randomUUID();
    var claims =
        jdbc.query(
            """
with ready as (
  select d.id,w.target_url,w.secret_ciphertext from outbound_webhook_delivery d
  join outbound_webhook w on w.id=d.webhook_id join projects p on p.id=w.project_id
  where d.outcome='PENDING' and d.attempts<? and d.next_attempt_at<=clock_timestamp()
    and p.archived_at is null and p.trashed_at is null
  order by d.next_attempt_at,d.id limit 1 for update of d skip locked
)
update outbound_webhook_delivery d set attempts=d.attempts+1,claim_token=?,
  next_attempt_at=clock_timestamp()+interval '2 minutes'
from ready where d.id=ready.id
returning d.id,d.attempts,ready.target_url,ready.secret_ciphertext,d.event_type,d.payload
""",
            (row, index) ->
                new Claim(
                    row.getObject(1, UUID.class),
                    claimToken,
                    row.getInt(2),
                    row.getString(3),
                    row.getString(4),
                    row.getString(5),
                    row.getString(6)),
            MAX_ATTEMPTS,
            claimToken);
    if (claims.isEmpty()) return Optional.empty();
    Claim claim = claims.getFirst();
    jdbc.update(
        """
        insert into outbound_webhook_attempt(delivery_id,attempt,result) values(?,?,'INTERRUPTED')
        """,
        claim.id(),
        claim.attempt());
    return Optional.of(claim);
  }

  @Transactional
  public void finish(Claim claim, OutboundWebhookTransport.Result result) {
    boolean retry = result.retry() && claim.attempt() < MAX_ATTEMPTS;
    String outcome =
        retry ? "PENDING" : "DELIVERED".equals(result.result()) ? "DELIVERED" : "FAILED";
    int delay = retry ? RETRY_DELAY_SECONDS[claim.attempt() - 1] : 0;
    if (jdbc.update(
            """
            update outbound_webhook_delivery set outcome=?,claim_token=null,
              next_attempt_at=clock_timestamp()+make_interval(secs => ?)
            where id=? and claim_token=?
            """,
            outcome,
            delay,
            claim.id(),
            claim.claimToken())
        != 1) return;
    jdbc.update(
        """
        update outbound_webhook_attempt set status=?,result=? where delivery_id=? and attempt=?
        """,
        result.status(),
        result.result(),
        claim.id(),
        claim.attempt());
  }
}
