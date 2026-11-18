package com.sprintflowinsight.workspace.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/**
 * A task was created. {@code version} is the optimistic-lock version the task had when the
 * transaction committed. Users are referenced by id; see {@link NotificationRequested}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskCreated(
    UUID eventId,
    Long projectId,
    Long actorUserId,
    Instant occurredAt,
    Long taskId,
    Long version) {}
