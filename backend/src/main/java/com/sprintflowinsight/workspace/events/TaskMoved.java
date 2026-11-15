package com.sprintflowinsight.workspace.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/** Users are referenced by id; see {@link NotificationRequested}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskMoved(
    UUID eventId,
    Long projectId,
    Long actorUserId,
    Instant occurredAt,
    Long taskId,
    String title,
    String previousStatus,
    String status) {}
