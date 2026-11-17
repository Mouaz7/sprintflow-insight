package com.sprintflowinsight.workspace.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/** A task was moved to the recycle bin and is no longer visible on the board. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskDeleted(
    UUID eventId, Long projectId, Long actorUserId, Instant occurredAt, Long taskId) {}
