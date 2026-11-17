package com.sprintflowinsight.workspace.events;

import java.time.Instant;
import java.util.UUID;

/** Committed aggregate change; identifiers only, for read-model invalidation. */
public record WorkspaceChanged(
    UUID eventId,
    Long projectId,
    Long actorUserId,
    Instant occurredAt,
    String type,
    String entityType,
    Long entityId) {}
