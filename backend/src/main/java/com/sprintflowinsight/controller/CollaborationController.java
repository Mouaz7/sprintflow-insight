package com.sprintflowinsight.controller;

import com.sprintflowinsight.common.error.ApiException;

import com.sprintflowinsight.model.Notification;
import com.sprintflowinsight.model.Task;
import com.sprintflowinsight.model.TaskComment;
import com.sprintflowinsight.project.ProjectAccessService;
import com.sprintflowinsight.repository.TaskCommentRepository;
import com.sprintflowinsight.service.ActivityService;
import com.sprintflowinsight.service.NotificationService;
import com.sprintflowinsight.service.TaskService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@org.springframework.transaction.annotation.Transactional
public class CollaborationController {
  private final TaskService tasks;
  private final TaskCommentRepository comments;
  private final NotificationService notifications;
  private final ActivityService activity;
  private final ProjectAccessService access;

  public CollaborationController(
      TaskService tasks,
      TaskCommentRepository comments,
      NotificationService notifications,
      ActivityService activity,
      ProjectAccessService access) {
    this.tasks = tasks;
    this.comments = comments;
    this.notifications = notifications;
    this.activity = activity;
    this.access = access;
  }

  @GetMapping("/tasks/{taskId}/comments")
  public ResponseEntity<List<TaskComment>> comments(
      @PathVariable Long taskId, HttpSession session) {
    Task task = tasks.gettaskById(taskId).orElse(null);
    if (task == null) throw ApiException.notFound("Task not found.");
    access.read(task.getProjectId(), session);
    return ResponseEntity.ok(comments.findByTaskIdOrderByCreatedAtAsc(taskId));
  }

  @PostMapping("/tasks/{taskId}/comments")
  public ResponseEntity<?> addComment(
      @PathVariable Long taskId, @Valid @RequestBody CommentRequest request, HttpSession session) {
    Task task = tasks.gettaskById(taskId).orElse(null);
    if (task == null) throw ApiException.notFound("Task not found.");
    access.write(task.getProjectId(), session);
    String actor = access.email(session);
    TaskComment comment = new TaskComment();
    comment.setTaskId(taskId);
    comment.setProjectId(task.getProjectId());
    comment.setAuthorEmail(actor.trim().toLowerCase());
    comment.setMessage(request.message().trim());
    comment.setCreatedAt(Instant.now());
    TaskComment saved = comments.save(comment);
    activity.record(
        task.getProjectId(),
        "COMMENT_ADDED",
        "Commented on task “" + task.getTitle() + "”.",
        actor);
    notifications.notifyProjectOwner(
        task.getProjectId(), actor, "New comment on “" + task.getTitle() + "”.");
    notifications.notifyRecipient(
        task.getProjectId(),
        task.getAssigneeEmail(),
        actor,
        "New comment on “" + task.getTitle() + "”.");
    return ResponseEntity.ok(saved);
  }

  @PutMapping("/tasks/{taskId}/comments/{id}")
  public TaskComment editComment(
      @PathVariable Long taskId,
      @PathVariable Long id,
      @Valid @RequestBody CommentRequest request,
      HttpSession session) {
    TaskComment comment = editable(taskId, id, session);
    if (request.version() != null && !request.version().equals(comment.getVersion()))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT,
          "This item was changed by someone else. Refresh and try again.");
    comment.setMessage(request.message().trim());
    return comments.save(comment);
  }

  @DeleteMapping("/tasks/{taskId}/comments/{id}")
  public ResponseEntity<Void> deleteComment(
      @PathVariable Long taskId, @PathVariable Long id, HttpSession session) {
    comments.delete(editable(taskId, id, session));
    return ResponseEntity.noContent().build();
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

  @GetMapping("/notifications")
  public List<Notification> notifications(
      HttpSession session,
      @RequestParam(required = false) Integer page,
      @RequestParam(defaultValue = "100") int size) {
    return notifications.forRecipient(
        access.email(session), com.sprintflowinsight.OptionalPage.of(page, size));
  }

  @PutMapping("/notifications/{id}/read")
  public ResponseEntity<Void> markRead(@PathVariable Long id, HttpSession session) {
    notifications.markRead(id, access.email(session));
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/notifications/read")
  public ResponseEntity<Void> markAllRead(HttpSession session) {
    notifications.markAllRead(access.email(session));
    return ResponseEntity.noContent().build();
  }

  public record CommentRequest(
      @NotBlank(message = "Comment cannot be empty.")
          @Size(max = 1000, message = "Comments must be 1000 characters or fewer.")
          String message,
      Long version) {}
}
