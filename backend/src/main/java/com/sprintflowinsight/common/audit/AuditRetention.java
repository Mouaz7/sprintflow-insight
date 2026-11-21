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
    jdbc.queryForObject("select prune_audit_history(10000)", Integer.class);
  }
}
