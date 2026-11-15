package com.sprintflowinsight.workspace;

import com.sprintflowinsight.auth.AccountDeleting;
import com.sprintflowinsight.workspace.activity.ActivityService;
import com.sprintflowinsight.workspace.collaboration.*;
import com.sprintflowinsight.workspace.project.*;
import com.sprintflowinsight.workspace.task.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class WorkspaceAccountListener {
  private final ProjectRepository projects;
  private final TaskCommentRepository comments;
  private final TaskStatusHistoryRepository history;
  private final ActivityService activity;
  private final ProjectMemberRepository members;
  private final TaskRepository tasks;
  private final RetrospectiveRepository retrospectives;

  public WorkspaceAccountListener(
      ProjectRepository projects,
      TaskCommentRepository comments,
      TaskStatusHistoryRepository history,
      ActivityService activity,
      ProjectMemberRepository members,
      TaskRepository tasks,
      RetrospectiveRepository retrospectives) {
    this.projects = projects;
    this.comments = comments;
    this.history = history;
    this.activity = activity;
    this.members = members;
    this.tasks = tasks;
    this.retrospectives = retrospectives;
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void deleting(AccountDeleting event) {
    String email = event.email();
    if (!projects.findByOwnerEmail(email).isEmpty())
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT,
          "Delete or transfer your projects before deleting this account.");
    comments.deleteByAuthorEmail(email);
    history.anonymizeActorEmail(email);
    activity.deleteForActor(email);
    members.deleteByEmailIgnoreCase(email);
    tasks.unassignEverywhere(email);
    retrospectives.anonymizeAuthor(email);
  }
}
