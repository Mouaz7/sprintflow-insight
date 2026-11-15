package com.sprintflowinsight.workspace;

import com.sprintflowinsight.workspace.events.NotificationRequested;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class NotificationRequests {
  private final ApplicationEventPublisher events;

  public NotificationRequests(ApplicationEventPublisher events) {
    this.events = events;
  }

  public void notifyProjectOwner(Long projectId, String actor, String message) {
    events.publishEvent(
        new NotificationRequested(
            UUID.randomUUID(), projectId, actor, Instant.now(), null, message, true));
  }

  public void notifyRecipient(Long projectId, String recipient, String actor, String message) {
    if (recipient != null)
      events.publishEvent(
          new NotificationRequested(
              UUID.randomUUID(), projectId, actor, Instant.now(), recipient, message, false));
  }
}
