package com.sprintflowinsight.workspace.collaboration;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.activity.ActivityService;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskService;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@org.springframework.stereotype.Service
public class CollaborationService {
  private final MentionService mentions;
  private final com.sprintflowinsight.common.audit.AuditService audit;
  private final com.sprintflowinsight.workspace.project.ProjectMutationService mutations;
  private final TaskService tasks;
  private final TaskCommentRepository comments;
  private final org.springframework.context.ApplicationEventPublisher events;
  private final ActivityService activity;
  private final ProjectAccessService access;
  private final com.sprintflowinsight.workspace.NotificationRequests notifications;

  public CollaborationService(
      TaskService tasks,
      MentionService mentions,
      com.sprintflowinsight.workspace.project.ProjectMutationService mutations,
      TaskCommentRepository comments,
      org.springframework.context.ApplicationEventPublisher events,
      ActivityService activity,
      ProjectAccessService access,
      com.sprintflowinsight.workspace.NotificationRequests notifications,
      com.sprintflowinsight.common.audit.AuditService audit) {
    this.audit = audit;
    this.notifications = notifications;
    this.mentions = mentions;
    this.mutations = mutations;
    this.tasks = tasks;
    this.comments = comments;
    this.events = events;
    this.activity = activity;
    this.access = access;
  }

  public ResponseEntity<List<TaskComment>> comments(Long taskId, HttpSession session) {
    Task task = tasks.gettaskById(taskId).orElse(null);
    if (task == null) throw ApiException.notFound("Task not found.");
    access.read(task.getProjectId(), session);
    return ResponseEntity.ok(comments.findByTaskIdOrderByCreatedAtAsc(taskId));
  }

  public ResponseEntity<?> addComment(Long taskId, CommentRequest request, HttpSession session) {
    Task task = tasks.gettaskById(taskId).orElse(null);
    if (task == null) throw ApiException.notFound("Task not found.");
    access.write(task.getProjectId(), session);
    mutations.lock(task.getProjectId());
    access.forget(task.getProjectId());
    access.write(task.getProjectId(), session);
    return ResponseEntity.ok(
        saveComment(task, request.message(), access.email(session), access.userId(session)));
  }

  /** Internal automation callers hold the project lock and have verified the task's scope. */
  @Transactional(propagation = Propagation.MANDATORY)
  public TaskComment addAutomationComment(Task task, String message) {
    return saveComment(task, message, "automation@sprintflow.invalid", null);
  }

  private TaskComment saveComment(Task task, String message, String actor, Long actorId) {
    Long taskId = task.getId();
    TaskComment comment = new TaskComment();
    comment.setTaskId(taskId);
    comment.setProjectId(task.getProjectId());
    comment.setAuthorEmail(actor.trim().toLowerCase());
    comment.setMessage(message.trim());
    comment.setCreatedAt(Instant.now());
    TaskComment saved = comments.saveAndFlush(comment);
    if (actorId != null) mentions.commentSaved(saved, task.getTitle(), actorId);
    activity.record(
        task.getProjectId(),
        "COMMENT_ADDED",
        "Commented on task “" + task.getTitle() + "”.",
        actor);
    events.publishEvent(
        new com.sprintflowinsight.workspace.events.CommentAdded(
            java.util.UUID.randomUUID(),
            task.getProjectId(),
            actorId,
            Instant.now(),
            taskId,
            saved.getId(),
            task.getTitle(),
            notifications.userId(task.getAssigneeEmail())));
    return saved;
  }

  public TaskComment editComment(
      Long taskId, Long id, CommentRequest request, HttpSession session) {
    TaskComment comment = editable(taskId, id, session);
    if (request.version() != null && !request.version().equals(comment.getVersion()))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT,
          "This item was changed by someone else. Refresh and try again.");
    comment.setMessage(request.message().trim());
    TaskComment saved = comments.saveAndFlush(comment);
    mentions.commentSaved(
        saved, tasks.gettaskById(taskId).orElseThrow().getTitle(), access.userId(session));
    changed(saved, "CommentUpdated", session);
    return saved;
  }

  public ResponseEntity<Void> deleteComment(Long taskId, Long id, HttpSession session) {
    TaskComment comment = editable(taskId, id, session);
    comments.delete(comment);
    changed(comment, "CommentDeleted", session);
    return ResponseEntity.noContent().build();
  }

  private void changed(TaskComment comment, String type, HttpSession session) {
    if (!comment.getAuthorEmail().equalsIgnoreCase(access.email(session))) {
      var caller =
          access.require(
              comment.getProjectId(),
              session,
              com.sprintflowinsight.workspace.project.AccessContext.Need.MANAGE);
      audit.record(
          comment.getProjectId(),
          caller.userId(),
          caller.role().label(),
          type.equals("CommentUpdated") ? "COMMENT_MODERATED" : "COMMENT_REMOVED",
          "comment",
          comment.getId(),
          java.util.Map.of());
    }
    events.publishEvent(
        new com.sprintflowinsight.workspace.events.WorkspaceChanged(
            java.util.UUID.randomUUID(),
            comment.getProjectId(),
            access.userId(session),
            Instant.now(),
            type,
            "task",
            comment.getTaskId()));
  }

  private TaskComment editable(Long taskId, Long id, HttpSession session) {
    Task task =
        tasks
            .gettaskById(taskId)
            .orElseThrow(
                () ->
                    new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND));
    access.write(task.getProjectId(), session);
    mutations.lock(task.getProjectId());
    access.forget(task.getProjectId());
    access.write(task.getProjectId(), session);
    TaskComment comment =
        comments
            .findById(id)
            .filter(
                c -> taskId.equals(c.getTaskId()) && task.getProjectId().equals(c.getProjectId()))
            .orElseThrow(
                () ->
                    new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND));
    String role = access.role(task.getProjectId(), session);
    if (!comment.getAuthorEmail().equalsIgnoreCase(access.email(session))
        && !java.util.Set.of("Owner", "Admin").contains(role))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN, "You can only edit your own comments.");
    return comment;
  }
}
