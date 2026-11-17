package com.sprintflowinsight.realtime;

import com.sprintflowinsight.workspace.events.CommentAdded;
import com.sprintflowinsight.workspace.events.SprintStatusChanged;
import com.sprintflowinsight.workspace.events.TaskCreated;
import com.sprintflowinsight.workspace.events.TaskDeleted;
import com.sprintflowinsight.workspace.events.TaskMoved;
import com.sprintflowinsight.workspace.events.TaskUpdated;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Turns durable workspace events into realtime messages. {@link ApplicationModuleListener} runs
 * after the domain transaction commits, in its own transaction, and is retried from the event
 * registry when it fails (ADR 0006), so clients only hear about committed changes.
 */
@Component
public class RealtimeEventListener {
  private final RealtimePublisher publisher;

  public RealtimeEventListener(RealtimePublisher publisher) {
    this.publisher = publisher;
  }

  @ApplicationModuleListener
  public void created(TaskCreated event) {
    publisher.publish(
        event.eventId(),
        event.projectId(),
        "TaskCreated",
        event.taskId(),
        event.version(),
        event.occurredAt());
  }

  @ApplicationModuleListener
  public void moved(TaskMoved event) {
    publisher.publish(
        event.eventId(),
        event.projectId(),
        "TaskMoved",
        event.taskId(),
        event.version(),
        event.occurredAt());
  }

  @ApplicationModuleListener
  public void updated(TaskUpdated event) {
    publisher.publish(
        event.eventId(),
        event.projectId(),
        "TaskUpdated",
        event.taskId(),
        event.version(),
        event.occurredAt());
  }

  @ApplicationModuleListener
  public void deleted(TaskDeleted event) {
    publisher.publish(
        event.eventId(),
        event.projectId(),
        "TaskDeleted",
        event.taskId(),
        null,
        event.occurredAt());
  }

  @ApplicationModuleListener
  public void commented(CommentAdded event) {
    publisher.publish(
        event.eventId(),
        event.projectId(),
        "CommentAdded",
        event.taskId(),
        null,
        event.occurredAt());
  }

  @ApplicationModuleListener
  public void sprintStatus(SprintStatusChanged event) {
    publisher.publish(
        event.eventId(),
        event.projectId(),
        "SprintStatusChanged",
        event.sprintId(),
        event.version(),
        event.occurredAt());
  }

  @ApplicationModuleListener
  public void changed(com.sprintflowinsight.workspace.events.WorkspaceChanged event) {
    publisher.publish(
        event.eventId(),
        event.projectId(),
        event.type(),
        event.entityId(),
        null,
        event.occurredAt());
  }

  @ApplicationModuleListener
  public void notification(com.sprintflowinsight.notifications.NotificationDelivered event) {
    publisher.publish(
        event.eventId(), event.projectId(), "NotificationsChanged", null, null, event.occurredAt());
  }
}
