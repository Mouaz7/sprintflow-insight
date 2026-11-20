package com.sprintflowinsight.workspace.collaboration;

import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.sprint.SprintRepository;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@org.springframework.transaction.annotation.Transactional
@org.springframework.stereotype.Service
public class RetrospectiveService {
  private final RetrospectiveRepository entries;
  private final SprintRepository sprints;
  private final ProjectAccessService access;
  private final com.sprintflowinsight.common.audit.AuditService audit;
  private final com.sprintflowinsight.workspace.project.ProjectMutationService mutations;

  public RetrospectiveService(
      RetrospectiveRepository entries,
      SprintRepository sprints,
      ProjectAccessService access,
      com.sprintflowinsight.common.audit.AuditService audit,
      com.sprintflowinsight.workspace.project.ProjectMutationService mutations) {
    this.audit = audit;
    this.mutations = mutations;
    this.entries = entries;
    this.sprints = sprints;
    this.access = access;
  }

  public List<RetrospectiveEntry> list(Long projectId, Long sprintId, HttpSession session) {
    access.read(projectId, session);
    sprint(projectId, sprintId);
    return entries.findByProjectIdAndSprintIdOrderByCreatedAtAsc(projectId, sprintId);
  }

  public RetrospectiveEntry add(Long projectId, EntryRequest request, HttpSession session) {
    access.write(projectId, session);
    sprint(projectId, request.sprintId());
    validateCategory(request.category());
    var entry = new RetrospectiveEntry();
    entry.setProjectId(projectId);
    entry.setSprintId(request.sprintId());
    entry.setAuthorEmail(access.email(session));
    entry.setCategory(request.category());
    entry.setMessage(request.message().trim());
    entry.setCreatedAt(Instant.now());
    return entries.save(entry);
  }

  public RetrospectiveEntry update(
      Long projectId, Long id, EntryRequest request, HttpSession session) {
    var entry = editable(projectId, id, session);
    if (!entry.getSprintId().equals(request.sprintId()))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "A retrospective entry cannot move to another sprint.");
    if (request.version() != null && !request.version().equals(entry.getVersion()))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This item was changed by someone else. Refresh and try again.");
    validateCategory(request.category());
    entry.setCategory(request.category());
    entry.setMessage(request.message().trim());
    moderated(entry, "RETROSPECTIVE_MODERATED", session);
    return entries.save(entry);
  }

  public void delete(Long projectId, Long id, HttpSession session) {
    var entry = editable(projectId, id, session);
    moderated(entry, "RETROSPECTIVE_REMOVED", session);
    entries.delete(entry);
  }

  private RetrospectiveEntry editable(Long projectId, Long id, HttpSession session) {
    access.write(projectId, session);
    mutations.lock(projectId);
    access.forget(projectId);
    access.write(projectId, session);
    var entry =
        entries
            .findById(id)
            .filter(e -> projectId.equals(e.getProjectId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    sprint(projectId, entry.getSprintId());
    String role = access.role(projectId, session);
    if (!entry.getAuthorEmail().equalsIgnoreCase(access.email(session))
        && !Set.of("Owner", "Admin").contains(role))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "You can only edit your own retrospective entries.");
    return entry;
  }

  private void moderated(RetrospectiveEntry entry, String action, HttpSession session) {
    if (!entry.getAuthorEmail().equalsIgnoreCase(access.email(session))) {
      var caller =
          access.require(
              entry.getProjectId(),
              session,
              com.sprintflowinsight.workspace.project.AccessContext.Need.MANAGE);
      audit.record(
          entry.getProjectId(),
          caller.userId(),
          caller.role().label(),
          action,
          "retrospective",
          entry.getId(),
          Map.of());
    }
  }

  private void sprint(Long projectId, Long sprintId) {
    if (sprintId == null
        || sprints
            .findById(sprintId)
            .filter(s -> projectId.equals(s.getProjectId()) && s.getTrashedAt() == null)
            .isEmpty())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a sprint in this project.");
  }

  private void validateCategory(String value) {
    if (!Set.of("well", "improve", "actions").contains(value))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Choose a valid retrospective category.");
  }
}
