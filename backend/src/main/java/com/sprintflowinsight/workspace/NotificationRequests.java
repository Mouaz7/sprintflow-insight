package com.sprintflowinsight.workspace;

import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.auth.UserRepository;
import com.sprintflowinsight.workspace.events.NotificationRequested;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class NotificationRequests {
  private final ApplicationEventPublisher events;
  private final UserRepository users;

  public NotificationRequests(ApplicationEventPublisher events, UserRepository users) {
    this.events = events;
    this.users = users;
  }

  public void notifyProjectOwner(Long projectId, String actor, String message) {
    events.publishEvent(
        new NotificationRequested(
            UUID.randomUUID(), projectId, userId(actor), Instant.now(), null, message, true));
  }

  public void notifyRecipient(Long projectId, String recipient, String actor, String message) {
    Long recipientId = userId(recipient);
    if (recipientId != null)
      events.publishEvent(
          new NotificationRequested(
              UUID.randomUUID(),
              projectId,
              userId(actor),
              Instant.now(),
              recipientId,
              message,
              false));
  }

  /**
   * The id of the registered account with this email, or {@code null} for blank, system or
   * unregistered addresses. Events carry ids so delivery uses the account's current email.
   */
  public Long userId(String email) {
    if (email == null || email.isBlank()) return null;
    return users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).map(User::getId).orElse(null);
  }
}
