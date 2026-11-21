package com.sprintflowinsight.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.support.MigrationFixture;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditIntegrityMigrationTest {
  @Test
  void backfillsExistingAuditEventsWithoutChangingTheirContents() throws Exception {
    try (var fixture = new MigrationFixture()) {
      fixture.migrateTo("33");
      JdbcTemplate jdbc = new JdbcTemplate(fixture.dataSource());
      jdbc.update(
          """
insert into admin_audit_event(project_id,actor_user_id,actor_role,action,target_type,created_at)
values (51,12,'Owner','PROJECT_UPDATED','project',now()-interval '91 days'),
       (52,12,'Owner','PROJECT_UPDATED','project',now()-interval '30 days'),
       (51,15,'Admin','PROJECT_ARCHIVED','project',now()-interval '5 days')
""");
      String events =
          "select id,project_id,actor_user_id,actor_role,action,target_type,created_at"
              + " from admin_audit_event order by id";
      var before = jdbc.queryForList(events);

      assertThat(fixture.migrateTo("34").migrationsExecuted).isEqualTo(1);
      assertThat(jdbc.queryForList(events)).isEqualTo(before);
      assertThat(
              jdbc.queryForList(
                  "select chain_position from admin_audit_event order by id", Long.class))
          .containsExactly(1L, 1L, 2L);
      assertThat(
              jdbc.queryForList(
                  """
                  select chain_hash=sha256(previous_hash||audit_event_bytes(e))
                  from admin_audit_event e order by id
                  """,
                  Boolean.class))
          .containsExactly(true, true, true);
      assertThat(jdbc.queryForObject("select prune_audit_history(1000)", Integer.class))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  """
                  select c.floor_hash=e.previous_hash from admin_audit_chain c
                  join admin_audit_event e on e.project_id=c.scope_id
                  where c.scope_id=51
                  """,
                  Boolean.class))
          .isTrue();
    }
  }
}
