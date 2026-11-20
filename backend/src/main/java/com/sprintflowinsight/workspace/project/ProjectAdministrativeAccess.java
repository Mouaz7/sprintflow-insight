package com.sprintflowinsight.workspace.project;

import com.sprintflowinsight.common.audit.AuditService;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Serializes administrative authorization with membership and project changes. */
@Service
public class ProjectAdministrativeAccess {
  private final ProjectAccessService access;
  private final ProjectRepository projects;
  private final AuditService audit;

  public ProjectAdministrativeAccess(
      ProjectAccessService access, ProjectRepository projects, AuditService audit) {
    this.access = access;
    this.projects = projects;
    this.audit = audit;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public AccessContext lock(Long id, HttpSession session, AccessContext.Need need) {
    access.require(id, session, need);
    projects.lockById(id).orElseThrow();
    access.forget(id);
    return access.require(id, session, need);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      AccessContext caller, String action, String target, Long targetId, Map<String, ?> details) {
    audit.record(
        caller.projectId(),
        caller.userId(),
        caller.role().label(),
        action,
        target,
        targetId,
        details);
  }
}
