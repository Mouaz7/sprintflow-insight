package com.sprintflowinsight.common.audit;

import java.time.LocalDate;
import java.time.ZoneOffset;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class AuditAnchorJob {
  private final AuditIntegrityService integrity;

  AuditAnchorJob(AuditIntegrityService integrity) {
    this.integrity = integrity;
  }

  @Scheduled(cron = "0 5 0 * * *", zone = "UTC")
  @SchedulerLock(name = "admin-audit-anchor", lockAtMostFor = "PT5M")
  void capture() {
    integrity.anchor(LocalDate.now(ZoneOffset.UTC));
  }
}
