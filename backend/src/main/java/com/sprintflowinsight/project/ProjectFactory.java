package com.sprintflowinsight.project;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.common.error.ErrorCode;
import com.sprintflowinsight.service.ActivityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The single way a project comes into existence: unique name, owner membership, activity. */
@Service
public class ProjectFactory {
  private final ProjectRepository projects;
  private final ProjectMemberRepository members;
  private final ActivityService activity;

  public ProjectFactory(
      ProjectRepository projects, ProjectMemberRepository members, ActivityService activity) {
    this.projects = projects;
    this.members = members;
    this.activity = activity;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Project create(String name, String description, String ownerEmail) {
    return create(name, description, ownerEmail, null);
  }

  /** @param activitySummary overrides the default "Created project" activity text */
  @Transactional(propagation = Propagation.MANDATORY)
  public Project create(
      String name, String description, String ownerEmail, String activitySummary) {
    String trimmed = name.trim();
    if (projects.findByName(trimmed).isPresent())
      throw new ApiException(ErrorCode.CONFLICT, "A project with this name already exists.");
    Project project = projects.save(new Project(trimmed, description, ownerEmail));
    ProjectMember owner = new ProjectMember();
    owner.setProjectId(project.getId());
    owner.setEmail(ownerEmail);
    owner.setRole(ProjectRole.OWNER.label());
    members.save(owner);
    activity.record(
        project.getId(),
        "PROJECT_CREATED",
        activitySummary != null ? activitySummary : "Created project “" + project.getName() + "”.",
        ownerEmail);
    return project;
  }
}
