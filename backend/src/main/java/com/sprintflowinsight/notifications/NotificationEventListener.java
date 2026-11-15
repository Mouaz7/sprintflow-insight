package com.sprintflowinsight.notifications;

import com.sprintflowinsight.auth.AccountDeleting;
import com.sprintflowinsight.auth.UserEmailChanged;
import com.sprintflowinsight.workspace.events.*;
import jakarta.persistence.EntityManager;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class NotificationEventListener {
  /**
   * Outstanding requests addressed only to the deleted user can never be delivered. Rows are
   * matched by event type and the exact serialized id, as plain text so no row is parsed as JSON.
   * Comment and task events stay: they also notify the project owner, and delivery skips the
   * deleted user.
   */
  static final String UNDELIVERABLE_REQUESTS =
      "delete from event_publication where completion_date is null and event_type = ?1"
          + " and serialized_event ~ ?2";

  private final NotificationService notifications;
  private final NotificationRepository repository;
  private final EntityManager entities;

  public NotificationEventListener(
      NotificationService notifications,
      NotificationRepository repository,
      EntityManager entities) {
    this.notifications = notifications;
    this.repository = repository;
    this.entities = entities;
  }

  @ApplicationModuleListener
  public void moved(TaskMoved event) {
    notifications.notifyProjectOwner(
        event.projectId(),
        event.actorUserId(),
        "Moved task “" + event.title() + "” to " + event.status() + ".",
        event.eventId());
  }

  @ApplicationModuleListener
  public void commented(CommentAdded event) {
    String message = "New comment on “" + event.title() + "”.";
    notifications.notifyProjectOwner(
        event.projectId(), event.actorUserId(), message, event.eventId());
    notifications.notifyRecipient(
        event.projectId(), event.assigneeUserId(), event.actorUserId(), message, event.eventId());
  }

  @ApplicationModuleListener
  public void requested(NotificationRequested event) {
    if (event.projectOwner())
      notifications.notifyProjectOwner(
          event.projectId(), event.actorUserId(), event.message(), event.eventId());
    else
      notifications.notifyRecipient(
          event.projectId(),
          event.recipientUserId(),
          event.actorUserId(),
          event.message(),
          event.eventId());
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void deleting(ProjectDeleting event) {
    repository.deleteByProjectId(event.projectId());
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void deleting(AccountDeleting event) {
    repository.deleteByRecipientEmail(event.email());
    entities
        .createNativeQuery(UNDELIVERABLE_REQUESTS)
        .setParameter(1, NotificationRequested.class.getName())
        .setParameter(2, "\"recipientUserId\":" + event.userId() + "[,}]")
        .executeUpdate();
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void emailChanged(UserEmailChanged event) {
    entities.flush();
    entities
        .createQuery(
            "update Notification n set n.recipientEmail=:next where"
                + " lower(n.recipientEmail)=lower(:previous)")
        .setParameter("next", event.nextEmail())
        .setParameter("previous", event.previousEmail())
        .executeUpdate();
  }
}
