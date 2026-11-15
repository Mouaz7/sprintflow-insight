package com.sprintflowinsight.workspace.project;


import com.sprintflowinsight.notifications.NotificationRepository;
import com.sprintflowinsight.reporting.SharedReportRepository;
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
  private final ProjectAccessService access;
  private final ProjectRepository projects;
  private final ProjectMemberRepository members;
  private final UserStoryRepository stories;
  private final SprintRepository sprints;
  private final TaskRepository tasks;
  private final TaskCommentRepository comments;
  private final TaskAttachmentRepository attachments;
  private final TaskStatusHistoryRepository history;
  private final ActivityEventRepository activities;
  private final NotificationRepository notifications;
  private final RetrospectiveRepository retrospectives;
  private final SharedReportRepository reports;
  private final com.sprintflowinsight.workspace.attachment.FileDeletionService deletions;

  public ProjectDeletionService(
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
      NotificationRepository notifications,
      RetrospectiveRepository retrospectives,
      SharedReportRepository reports,
      com.sprintflowinsight.workspace.attachment.FileDeletionService deletions) {
    this.retrospectives = retrospectives;
    this.reports = reports;
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
    this.notifications = notifications;
  }

  @Transactional
  public void delete(Long projectId, HttpSession session) {
    if (!projects
        .findById(projectId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."))
        .getOwnerEmail()
        .equalsIgnoreCase(access.email(session))) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the project owner can delete this project.");
    }
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
    notifications.deleteByProjectId(projectId);
    members.deleteByProjectId(projectId);
    retrospectives.deleteByProjectId(projectId);
    reports.deleteByProjectId(projectId);
    projects.deleteById(projectId);
    deletions.enqueue(storageKeys);
  }
}
