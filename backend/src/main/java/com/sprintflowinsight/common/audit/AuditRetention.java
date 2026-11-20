package com.sprintflowinsight.common.audit;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class AuditRetention {
  private final JdbcTemplate jdbc;

  AuditRetention(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Scheduled(cron = "0 15 3 * * *")
  @SchedulerLock(name = "admin-audit-retention", lockAtMostFor = "PT5M")
  void removeExpired() {
    jdbc.update(
        "delete from admin_audit_event where id in (select id from admin_audit_event where"
            + " created_at < now()-interval '90 days' order by created_at,id limit 10000)");
  }
}
