package com.sprintflowinsight.workspace.task;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.NotificationRequests;
import com.sprintflowinsight.workspace.activity.ActivityService;
import com.sprintflowinsight.workspace.attachment.FileDeletionService;
import com.sprintflowinsight.workspace.attachment.TaskAttachment;
import com.sprintflowinsight.workspace.attachment.TaskAttachmentRepository;
import com.sprintflowinsight.workspace.collaboration.TaskCommentRepository;
import com.sprintflowinsight.workspace.events.TaskCreated;
import com.sprintflowinsight.workspace.events.TaskMoved;
import com.sprintflowinsight.workspace.events.TaskUpdated;
import com.sprintflowinsight.workspace.project.ProjectMutationService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
  private final org.springframework.context.ApplicationEventPublisher events;
  private final NotificationRequests notifications;
  private final ProjectMutationService mutations;
  private final FileDeletionService deletions;
  private final TaskNumberAllocator numbers;

  private final TaskRepository taskRepository;
  private final TaskStatusHistoryRepository historyRepository;
  private final ActivityService activityService;
  private final TaskCommentRepository commentsRepository;
  private final TaskAttachmentRepository attachmentsRepository;

  @Autowired
  public TaskService(
      org.springframework.context.ApplicationEventPublisher events,
      TaskRepository taskRepository,
      TaskStatusHistoryRepository historyRepository,
      ActivityService activityService,
      TaskCommentRepository commentsRepository,
      TaskAttachmentRepository attachmentsRepository,
      NotificationRequests notifications,
      ProjectMutationService mutations,
      FileDeletionService deletions,
      TaskNumberAllocator numbers) {
    this.numbers = numbers;
    this.events = events;
    this.notifications = notifications;
    this.mutations = mutations;
    this.deletions = deletions;

    this.taskRepository = taskRepository;
    this.historyRepository = historyRepository;
    this.activityService = activityService;
    this.commentsRepository = commentsRepository;
    this.attachmentsRepository = attachmentsRepository;
  }

  public List<Task> getTasksForProject(Long projectId) {
    return taskRepository.findByProjectId(projectId);
  }

  @Transactional
  public Task createTask(Task task, String actorEmail) {
    Instant now = Instant.now();
    mutations.lock(task.getProjectId());

    task.setId(null);
    task.setVersion(null);
    task.setCreatedAt(now);
    task.setStartedAt(null);
    task.setCompletedAt(null);
    applyWorkflowTimestamps(task, task.getStatus(), now);
    TaskNumberAllocator.Allocation number = numbers.next(task.getProjectId());
    task.setNumber(number.number());
    task.setProjectKey(number.projectKey());
    Task saved = taskRepository.save(task);
    recordHistory(saved, saved.getStatus(), now, actorEmail);
    events.publishEvent(
        new TaskCreated(
            UUID.randomUUID(),
            saved.getProjectId(),
            notifications.userId(actorEmail),
            now,
            saved.getId(),
            saved.getVersion()));
    notifications.notifyRecipient(
        saved.getProjectId(),
        saved.getAssigneeEmail(),
        actorEmail,
        "You were assigned task “" + saved.getTitle() + "”.");
    activityService.record(
        saved.getProjectId(),
        "TASK_CREATED",
        "Created task “" + saved.getTitle() + "”.",
        actorEmail);
    return saved;
  }

  @Transactional
  public Task updateTask(Task task, Task changes, String actorEmail) {
    return updateTask(task, changes, actorEmail, List.of());
  }

  @Transactional
  public Task updateTask(Task task, Task changes, String actorEmail, List<Long> automationTrail) {
    mutations.lock(task.getProjectId());
    Instant now = Instant.now();
    boolean previousBlocked = task.isBlocked();
    String previousStatus = task.getStatus();
    boolean historyChanged =
        !Objects.equals(task.getSprintId(), changes.getSprintId())
            || task.getStoryPoints() != changes.getStoryPoints()
            || !Objects.equals(previousStatus, changes.getStatus());
    String previousAssignee = task.getAssigneeEmail();
    task.setLabels(changes.getLabels());
    task.setChecklist(changes.getChecklist());
    task.setTitle(changes.getTitle());
    task.setDescription(changes.getDescription());
    task.setProjectId(changes.getProjectId());
    task.setSprintId(changes.getSprintId());
    task.setStoryId(changes.getStoryId());
    task.setAssigneeEmail(changes.getAssigneeEmail());
    task.setStoryPoints(changes.getStoryPoints());
    task.setDueAt(changes.getDueAt());
    task.setBlocked(changes.isBlocked());
    task.setBlockReason(changes.getBlockReason());
    boolean moved = changes.getStatus() != null && !changes.getStatus().equals(previousStatus);
    if (moved) {
      task.setStatus(changes.getStatus());
      applyWorkflowTimestamps(task, changes.getStatus(), now);

      activityService.record(
          task.getProjectId(),
          "TASK_STATUS",
          "Moved task “" + task.getTitle() + "” to " + changes.getStatus() + ".",
          actorEmail);
    }
    if (previousBlocked != task.isBlocked())
      activityService.record(
          task.getProjectId(),
          task.isBlocked() ? "TASK_BLOCKED" : "TASK_UNBLOCKED",
          (task.isBlocked() ? "Blocked task “" : "Unblocked task “") + task.getTitle() + "”.",
          actorEmail,
          "task",
          task.getId(),
          now);
    // Flushed so the events carry the version the client will see after this commit.
    Task saved = taskRepository.saveAndFlush(task);
    Long actorId = notifications.userId(actorEmail);
    if (moved)
      events.publishEvent(
          new TaskMoved(
              UUID.randomUUID(),
              saved.getProjectId(),
              actorId,
              now,
              saved.getId(),
              saved.getTitle(),
              previousStatus,
              saved.getStatus(),
              saved.getVersion(),
              automationTrail));
    else updated(saved, actorId, now);
    if (historyChanged) recordHistory(saved, saved.getStatus(), now, actorEmail);
    if (!Objects.equals(previousAssignee, saved.getAssigneeEmail()))
      notifications.notifyRecipient(
          saved.getProjectId(),
          saved.getAssigneeEmail(),
          actorEmail,
          "You were assigned task “" + saved.getTitle() + "”.");
    return saved;
  }

  @Transactional
  public void moveStoryTasks(Long projectId, Long storyId, Long sprintId, String actorEmail) {
    mutations.lock(projectId);
    Instant now = Instant.now();
    for (Task task : taskRepository.findAllByProjectIdAndStoryId(projectId, storyId)) {
      if (Objects.equals(task.getSprintId(), sprintId)) continue;
      task.setSprintId(sprintId);
      Task saved = taskRepository.saveAndFlush(task);
      recordHistory(saved, saved.getStatus(), now, actorEmail);
      updated(saved, notifications.userId(actorEmail), now);
    }
  }

  public Optional<Task> findByNumber(Long projectId, int number) {
    return taskRepository.findByProjectIdAndNumber(projectId, number);
  }

  public Optional<Task> gettaskById(long id) {
    return taskRepository.findById(id).filter(t -> t.getTrashedAt() == null);
  }

  @Transactional
  public void deleteTask(long id) {
    taskRepository
        .findById(id)
        .ifPresent(
            task -> {
              var storageKeys =
                  attachmentsRepository.findByTaskIdOrderByCreatedAtAsc(id).stream()
                      .map(TaskAttachment::getStorageKey)
                      .filter(Objects::nonNull)
                      .toList();
              deletions.enqueue(storageKeys);
              attachmentsRepository.deleteByTaskId(id);
              commentsRepository.deleteByTaskId(id);
              historyRepository.deleteByTaskId(id);
              taskRepository.delete(task);
            });
  }

  @Transactional
  public Task blockTask(long id, String reason, String actorEmail) {
    Task task =
        taskRepository.findById(id).orElseThrow(() -> ApiException.notFound("Task not found."));
    mutations.lock(task.getProjectId());

    task.setBlocked(true);
    task.setBlockReason(reason);
    Task saved = taskRepository.saveAndFlush(task);
    updated(saved, notifications.userId(actorEmail), Instant.now());
    activityService.record(
        saved.getProjectId(),
        "TASK_BLOCKED",
        "Blocked task “" + saved.getTitle() + "”: " + reason,
        actorEmail,
        "task",
        saved.getId(),
        Instant.now());
    return saved;
  }

  @Transactional
  public Task unblockTask(long id, String actorEmail) {
    Task task =
        taskRepository.findById(id).orElseThrow(() -> ApiException.notFound("Task not found."));
    mutations.lock(task.getProjectId());

    task.setBlocked(false);
    task.setBlockReason(null);
    Task saved = taskRepository.saveAndFlush(task);
    updated(saved, notifications.userId(actorEmail), Instant.now());
    activityService.record(
        saved.getProjectId(),
        "TASK_UNBLOCKED",
        "Unblocked task “" + saved.getTitle() + "”.",
        actorEmail,
        "task",
        saved.getId(),
        Instant.now());
    return saved;
  }

  /** Publishes {@link TaskUpdated} for a task that was saved and flushed in this transaction. */
  public void updated(Task saved, Long actorUserId, Instant now) {
    events.publishEvent(
        new TaskUpdated(
            UUID.randomUUID(),
            saved.getProjectId(),
            actorUserId,
            now,
            saved.getId(),
            saved.getVersion()));
  }

  private void applyWorkflowTimestamps(Task task, String status, Instant now) {
    if ("In Progress".equals(status) && task.getStartedAt() == null) task.setStartedAt(now);
    if ("Done".equals(status) && task.getCompletedAt() == null) task.setCompletedAt(now);
  }

  private void recordHistory(Task task, String status, Instant timestamp, String actorEmail) {
    TaskStatusHistory event = new TaskStatusHistory();
    event.setTaskId(task.getId());
    event.setProjectId(task.getProjectId());
    event.setSprintId(task.getSprintId());
    event.setStatus(status);
    event.setChangedAt(timestamp);
    event.setActorEmail(actorEmail);
    event.setStoryPoints(task.getStoryPoints());
    historyRepository.save(event);
  }
}
