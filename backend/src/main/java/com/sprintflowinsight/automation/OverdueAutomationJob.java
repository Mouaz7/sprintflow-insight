package com.sprintflowinsight.automation;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OverdueAutomationJob {
  private final JdbcTemplate jdbc;
  private final AutomationDispatch dispatch;

  OverdueAutomationJob(JdbcTemplate jdbc, AutomationDispatch dispatch) {
    this.jdbc = jdbc;
    this.dispatch = dispatch;
  }

  record Overdue(long projectId, long taskId, Instant dueAt) {
    AutomationSignal signal() {
      UUID id =
          UUID.nameUUIDFromBytes(
              ("due:" + projectId + ":" + taskId + ":" + dueAt).getBytes(StandardCharsets.UTF_8));
      return new AutomationSignal(
          id, projectId, taskId, AutomationRule.TriggerKind.OVERDUE, null, null, dueAt, List.of());
    }
  }

  @Scheduled(cron = "${app.automation.overdue-cron:0 */15 * * * *}", zone = "UTC")
  @SchedulerLock(name = "automation-overdue", lockAtMostFor = "PT10M")
  public void run() {
    LockAssert.assertLocked();
    runAt(Instant.now());
  }

  void runAt(Instant now) {
    long after = 0;
    while (true) {
      var overdue =
          jdbc.query(
              """
select t.project_id,t.id,t.due_at from task t join projects p on p.id=t.project_id
where t.id>? and t.status<>'Done' and t.trashed_at is null and t.due_at<?
  and p.trashed_at is null and p.archived_at is null
  and exists(select 1 from automation_rule r where r.project_id=p.id and r.enabled and r.trigger_kind='OVERDUE')
order by t.id limit 100
""",
              (row, index) ->
                  new Overdue(row.getLong(1), row.getLong(2), row.getTimestamp(3).toInstant()),
              after,
              Timestamp.from(now));
      if (overdue.isEmpty()) return;
      overdue.forEach(item -> dispatch.dispatch(item.signal()));
      after = overdue.getLast().taskId();
    }
  }
}
