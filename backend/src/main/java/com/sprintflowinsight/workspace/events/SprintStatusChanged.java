package com.sprintflowinsight.workspace.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/** A sprint moved between Planned, Active, Completed and Cancelled. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SprintStatusChanged(
    UUID eventId,
    Long projectId,
    Long actorUserId,
    Instant occurredAt,
    Long sprintId,
    String previousStatus,
    String status,
    Long version) {}
