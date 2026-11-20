package com.sprintflowinsight.workspace.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Members newly mentioned in a comment. Published inside the writing transaction and delivered
 * after commit through the event publication registry (the outbox), so a rolled-back comment never
 * notifies anyone and a failed delivery is retried. Users are referenced by id only; see {@link
 * NotificationRequested}.
 *
 * @param commentId the originating comment
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MentionAdded(
    UUID eventId,
    Long projectId,
    Long actorUserId,
    Instant occurredAt,
    Long taskId,
    Long commentId,
    String title,
    List<Long> mentionedUserIds) {

  public MentionAdded {
    mentionedUserIds = mentionedUserIds == null ? List.of() : List.copyOf(mentionedUserIds);
  }

  public boolean inComment() {
    return commentId != null;
  }
}
