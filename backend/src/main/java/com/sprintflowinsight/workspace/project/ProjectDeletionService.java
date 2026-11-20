package com.sprintflowinsight.workspace.project;

import com.sprintflowinsight.workspace.activity.ActivityEventRepository;
import com.sprintflowinsight.workspace.attachment.TaskAttachment;
import com.sprintflowinsight.workspace.attachment.TaskAttachmentRepository;
import com.sprintflowinsight.workspace.collaboration.RetrospectiveRepository;
import com.sprintflowinsight.workspace.collaboration.TaskCommentRepository;
import com.sprintflowinsight.workspace.sprint.SprintRepository;
import com.sprintflowinsight.workspace.story.UserStoryRepository;
import com.sprintflowinsight.workspace.task.TaskRepository;
import com.sprintflowinsight.workspace.task.TaskStatusHistoryRepository;
import jakarta.servlet.http.HttpSession;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectDeletionService {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ProjectDeletionService.class);
  private final org.springframework.context.ApplicationEventPublisher events;
  private final ProjectAccessService access;
  private final com.sprintflowinsight.common.audit.AuditService audit;
  private final ProjectRepository projects;
  private final ProjectMemberRepository members;
  private final UserStoryRepository stories;
  private final SprintRepository sprints;
  private final TaskRepository tasks;
  private final TaskCommentRepository comments;
  private final TaskAttachmentRepository attachments;
  private final TaskStatusHistoryRepository history;
  private final ActivityEventRepository activities;
  private final RetrospectiveRepository retrospectives;
  private final com.sprintflowinsight.workspace.attachment.FileDeletionService deletions;

  public ProjectDeletionService(
      org.springframework.context.ApplicationEventPublisher events,
      ProjectAccessService access,
      ProjectRepository projects,
      ProjectMemberRepository members,
      UserStoryRepository stories,
      SprintRepository sprints,
      TaskRepository tasks,
      TaskCommentRepository comments,
      TaskAttachmentRepository attachments,
      TaskStatusHistoryRepository history,
      ActivityEventRepository activities,
      RetrospectiveRepository retrospectives,
      com.sprintflowinsight.workspace.attachment.FileDeletionService deletions,
      com.sprintflowinsight.common.audit.AuditService audit) {
    this.audit = audit;
    this.events = events;
    this.retrospectives = retrospectives;
    this.deletions = deletions;

    this.access = access;
    this.projects = projects;
    this.members = members;
    this.stories = stories;
    this.sprints = sprints;
    this.tasks = tasks;
    this.comments = comments;
    this.attachments = attachments;
    this.history = history;
    this.activities = activities;
  }

  @Transactional
  public void delete(Long projectId, HttpSession session) {
    if (!projects
        .lockById(projectId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."))
        .getOwnerEmail()
        .equalsIgnoreCase(access.email(session))) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the project owner can delete this project.");
    }
    audit.record(
        projectId,
        access.userId(session),
        "Owner",
        "PROJECT_PURGED",
        "project",
        projectId,
        java.util.Map.of());
    var storageKeys =
        attachments.findByProjectId(projectId).stream()
            .map(TaskAttachment::getStorageKey)
            .filter(Objects::nonNull)
            .toList();
    // All database changes commit together; uploaded files survive a database rollback.
    attachments.deleteByProjectId(projectId);
    comments.deleteByProjectId(projectId);
    history.deleteByProjectId(projectId);
    tasks.deleteByProjectId(projectId);
    stories.deleteByProjectId(projectId);
    sprints.deleteByProjectId(projectId);
    activities.deleteByProjectId(projectId);
    events.publishEvent(new com.sprintflowinsight.workspace.events.ProjectDeleting(projectId));
    members.deleteByProjectId(projectId);
    retrospectives.deleteByProjectId(projectId);

    projects.deleteById(projectId);
    deletions.enqueue(storageKeys);
  }
}
