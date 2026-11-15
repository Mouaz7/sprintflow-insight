package com.sprintflowinsight.workspace.project;

import java.time.Instant;

/**
 * Everything an authorisation decision needs about one user and one project, loaded with a single
 * query.
 *
 * @param role {@code null} when the user is neither owner nor member
 */
public record AccessContext(
    Long projectId,
    String email,
    ProjectRole role,
    String ownerEmail,
    Instant archivedAt,
    Instant trashedAt) {

  public boolean hasAccess() {
    return role != null;
  }

  public boolean archived() {
    return archivedAt != null;
  }

  public boolean trashed() {
    return trashedAt != null;
  }

  /** What an operation requires. Each level implies membership. */
  public enum Need {
    /** Any member, including viewers; archived projects stay readable. */
    READ,
    /** Members who may edit work, on a project that is not archived. */
    WRITE,
    /** Owner or admin, on a project that is not archived. */
    MANAGE,
    /** Owner or admin; allowed on archived projects (archive/unarchive itself). */
    MANAGE_ARCHIVED,
    /** Owner only, on a project that is not archived. */
    OWNER,
    /** Owner only; allowed on archived projects (recycle bin, permanent deletion). */
    OWNER_ARCHIVED
  }
}
