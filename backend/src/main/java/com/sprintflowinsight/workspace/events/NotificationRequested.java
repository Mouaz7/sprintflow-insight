package com.sprintflowinsight.workspace.events;

import java.time.Instant;
import java.util.UUID;

public record NotificationRequested(
    UUID eventId,
    Long projectId,
    String actorEmail,
    Instant occurredAt,
    String recipientEmail,
    String message,
    boolean projectOwner) {}
