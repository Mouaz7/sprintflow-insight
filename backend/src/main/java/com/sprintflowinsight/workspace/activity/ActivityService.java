package com.sprintflowinsight.workspace.activity;

import com.sprintflowinsight.notifications.NotificationService;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ActivityService {
  private final ActivityEventRepository repository;
  private final NotificationService notifications;

  public ActivityService(ActivityEventRepository repository, NotificationService notifications) {
    this.repository = repository;
    this.notifications = notifications;
  }

  @Transactional
  public void record(Long projectId, String type, String summary, String actorEmail) {
    record(projectId, type, summary, actorEmail, null, null, Instant.now());
  }

  @Transactional
  public void record(
      Long projectId,
      String type,
      String summary,
      String actorEmail,
      String entityType,
      Long entityId,
      Instant timestamp) {
    if (projectId == null) return;
    ActivityEvent event = new ActivityEvent();
    event.setProjectId(projectId);
    event.setEventType(type);
    event.setSummary(summary);
    event.setActorEmail(actorEmail == null || actorEmail.isBlank() ? "Workspace" : actorEmail);
    var authentication =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    if (authentication != null
        && authentication.getPrincipal()
            instanceof com.sprintflowinsight.auth.UserIdentity identity)
      event.setActorUserId(identity.userId());
    event.setEntityType(entityType);
    event.setEntityId(entityId);
    event.setCreatedAt(timestamp);
    repository.save(event);
    if ("TASK_STATUS".equals(type)
        || "TASK_BLOCKED".equals(type)
        || "TASK_UNBLOCKED".equals(type)
        || "SPRINT_STATUS".equals(type)) {
      notifications.notifyProjectOwner(projectId, actorEmail, summary);
    }
  }

  public List<ActivityEvent> forProject(Long projectId) {
    return repository.findByProjectIdOrderByCreatedAtDesc(projectId);
  }

  @Transactional
  public ActivityEvent updateSummary(Long projectId, Long eventId, String summary) {
    ActivityEvent event = projectEvent(projectId, eventId);
    event.setSummary(summary.trim());
    return repository.save(event);
  }

  @Transactional
  public void deleteEvent(Long projectId, Long eventId) {
    repository.delete(projectEvent(projectId, eventId));
  }

  private ActivityEvent projectEvent(Long projectId, Long eventId) {
    ActivityEvent event =
        repository
            .findById(eventId)
            .filter(candidate -> projectId.equals(candidate.getProjectId()))
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Activity update not found."));
    return event;
  }

  @Transactional
  public void clearProject(Long projectId) {
    repository.deleteByProjectId(projectId);
  }

  public void deleteForActor(String email) {
    repository.deleteByActorEmail(email);
  }
}
