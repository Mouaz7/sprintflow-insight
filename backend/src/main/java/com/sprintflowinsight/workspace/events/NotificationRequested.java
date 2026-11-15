package com.sprintflowinsight.workspace.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/**
 * Users are referenced by id and resolved when the notification is delivered, so an email change
 * before delivery still reaches the user and stored publications hold no email address. Unknown
 * properties are ignored so publications persisted in the earlier email-based format still replay:
 * their user ids are {@code null}, which delivers project-owner notifications and skips the rest.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationRequested(
    UUID eventId,
    Long projectId,
    Long actorUserId,
    Instant occurredAt,
    Long recipientUserId,
    String message,
    boolean projectOwner) {}
