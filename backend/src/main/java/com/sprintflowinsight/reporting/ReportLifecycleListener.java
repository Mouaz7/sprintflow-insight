package com.sprintflowinsight.reporting;

import com.sprintflowinsight.auth.AccountDeleting;
import com.sprintflowinsight.auth.UserEmailChanged;
import com.sprintflowinsight.workspace.events.ProjectDeleting;
import jakarta.persistence.EntityManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class ReportLifecycleListener {
  private final SharedReportRepository reports;
  private final EntityManager entities;

  ReportLifecycleListener(SharedReportRepository reports, EntityManager entities) {
    this.reports = reports;
    this.entities = entities;
  }

  @EventListener
  public void deleting(ProjectDeleting event) {
    reports.deleteByProjectId(event.projectId());
  }

  @EventListener
  public void deleting(AccountDeleting event) {
    reports.deleteByCreatedBy(event.email());
  }

  @EventListener
  public void emailChanged(UserEmailChanged event) {
    entities.flush();
    entities
        .createQuery(
            "update SharedReport r set r.createdBy=:next where lower(r.createdBy)=lower(:previous)")
        .setParameter("next", event.nextEmail())
        .setParameter("previous", event.previousEmail())
        .executeUpdate();
  }
}
