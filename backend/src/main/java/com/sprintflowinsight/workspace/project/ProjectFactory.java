package com.sprintflowinsight.workspace.project;

import com.sprintflowinsight.auth.UserRepository;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.common.error.ErrorCode;
import com.sprintflowinsight.workspace.activity.ActivityService;
import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The single way a project comes into existence: unique name and key, owner membership, activity.
 */
@Service
public class ProjectFactory {
  public static final String KEY_IN_USE = "This project key is already in use.";
  /** Attempts for a creation whose derived key a concurrent creator keeps taking first. */
  static final int DERIVED_KEY_ATTEMPTS = 3;

  private static final String KEY_CONSTRAINT = "projects_key_unique";
  private final ProjectRepository projects;
  private final ProjectMemberRepository members;
  private final ActivityService activity;
  private final UserRepository users;
  private final TransactionTemplate transactions;

  public ProjectFactory(
      ProjectRepository projects,
      ProjectMemberRepository members,
      ActivityService activity,
      UserRepository users,
      PlatformTransactionManager transactionManager) {
    this.projects = projects;
    this.members = members;
    this.activity = activity;
    this.users = users;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  /**
   * Runs a project creation, which calls {@link #create}, in its own transaction. Two concurrent
   * creations can derive the same free key; the later insert then violates the unique key
   * constraint. When the caller did not request a key, that is not the client's conflict: the whole
   * transaction is retried, so the key is derived again from the now committed state, up to {@value
   * #DERIVED_KEY_ATTEMPTS} attempts. A requested key keeps its 409. Must be called outside a
   * transaction, otherwise the retry would join the failed one.
   */
  public <T> T inTransactionRetryingDerivedKey(String requestedKey, Supplier<T> creation) {
    for (int attempt = 1; ; attempt++) {
      try {
        return transactions.execute(status -> creation.get());
      } catch (DataIntegrityViolationException conflict) {
        if (requestedKey != null || attempt >= DERIVED_KEY_ATTEMPTS || !isKeyConflict(conflict))
          throw conflict;
      }
    }
  }

  static boolean isKeyConflict(Throwable exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof org.hibernate.exception.ConstraintViolationException hibernate
          && hibernate.getConstraintName() != null)
        return hibernate.getConstraintName().toLowerCase(Locale.ROOT).contains(KEY_CONSTRAINT);
      if (cause.getMessage() != null
          && cause.getMessage().toLowerCase(Locale.ROOT).contains(KEY_CONSTRAINT)) return true;
    }
    return false;
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
