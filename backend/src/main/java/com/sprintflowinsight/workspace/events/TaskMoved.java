package com.sprintflowinsight.workspace.events;

import java.time.Instant;
import java.util.UUID;

public record TaskMoved(
    UUID eventId,
    Long projectId,
    String actorEmail,
    Instant occurredAt,
    Long taskId,
    String title,
    String previousStatus,
    String status) {}
