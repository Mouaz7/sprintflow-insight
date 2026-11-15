package com.sprintflowinsight.workspace.project;

import com.sprintflowinsight.auth.UserRepository;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.common.error.ErrorCode;
import com.sprintflowinsight.workspace.activity.ActivityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single way a project comes into existence: unique name and key, owner membership, activity.
 */
@Service
public class ProjectFactory {
  public static final String KEY_IN_USE = "This project key is already in use.";
  private final ProjectRepository projects;
  private final ProjectMemberRepository members;
  private final ActivityService activity;
  private final UserRepository users;

  public ProjectFactory(
      ProjectRepository projects,
      ProjectMemberRepository members,
      ActivityService activity,
      UserRepository users) {
    this.projects = projects;
    this.members = members;
    this.activity = activity;
    this.users = users;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Project create(String name, String description, String ownerEmail) {
    return create(name, description, ownerEmail, null, null);
  }

  /**
   * @param key requested key in stored form, or {@code null} to derive a free one from the name
   * @param activitySummary overrides the default "Created project" activity text
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public Project create(
      String name, String description, String ownerEmail, String key, String activitySummary) {
    String trimmed = name.trim();
    if (projects.findByName(trimmed).isPresent())
      throw new ApiException(ErrorCode.CONFLICT, "A project with this name already exists.");
    String projectKey = key == null ? freeKey(trimmed) : requireFreeKey(key);
    Long ownerId =
        users
            .findByEmail(ownerEmail)
            .orElseThrow(
                () -> new ApiException(ErrorCode.UNAUTHENTICATED, "Sign in again to continue."))
            .getId();
    Project project = projects.save(new Project(trimmed, description, ownerEmail, projectKey));
    ProjectMember owner = new ProjectMember();
    owner.setProjectId(project.getId());
    owner.setUserId(ownerId);
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

  /** Validates a client-chosen key; the unique constraint still decides concurrent races. */
  String requireFreeKey(String key) {
    if (!ProjectKeys.isValid(key)) throw ApiException.badRequest(ProjectRequest.KEY_MESSAGE);
    if (projects.existsByProjectKey(key))
      throw ApiException.conflict(KEY_IN_USE);
    return key;
  }

  private String freeKey(String name) {
    String base = ProjectKeys.derive(name);
    java.util.Set<String> taken = new java.util.HashSet<>(projects.findKeysStartingWith(base));
    return ProjectKeys.unique(base, taken::contains);
  }
}
