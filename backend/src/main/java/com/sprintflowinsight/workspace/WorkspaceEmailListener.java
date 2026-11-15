package com.sprintflowinsight.workspace;

import com.sprintflowinsight.auth.UserEmailChanged;
import jakarta.persistence.EntityManager;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Moves account references in the same transaction as the profile change. */
@Service
public class WorkspaceEmailListener {
  private static final Set<String> VERSIONED_ENTITIES =
      Set.of("Task", "Project", "TaskComment", "RetrospectiveEntry");
  private final EntityManager entities;

  public WorkspaceEmailListener(EntityManager entities) {
    this.entities = entities;
  }

  @org.springframework.context.event.EventListener
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void moveReferences(UserEmailChanged change) {
    String previousEmail = change.previousEmail(), nextEmail = change.nextEmail();
    entities.flush();
    update("Project", "ownerEmail", previousEmail, nextEmail);
    update("ProjectMember", "email", previousEmail, nextEmail);
    update("Task", "assigneeEmail", previousEmail, nextEmail);
    update("TaskComment", "authorEmail", previousEmail, nextEmail);
    update("ActivityEvent", "actorEmail", previousEmail, nextEmail);
    update("TaskStatusHistory", "actorEmail", previousEmail, nextEmail);
    update("RetrospectiveEntry", "authorEmail", previousEmail, nextEmail);
  }

  private void update(String entity, String field, String previousEmail, String nextEmail) {
    String versionUpdate =
        VERSIONED_ENTITIES.contains(entity) ? ", item.version = item.version + 1" : "";
    entities
        .createQuery(
            "update "
                + entity
                + " item set item."
                + field
                + " = :nextEmail"
                + versionUpdate
                + " where lower(item."
                + field
                + ") = lower(:previousEmail)")
        .setParameter("nextEmail", nextEmail)
        .setParameter("previousEmail", previousEmail)
        .executeUpdate();
  }
}
