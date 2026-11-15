package com.sprintflowinsight.workspace.events;

import java.time.Instant;
import java.util.UUID;

public record CommentAdded(
    UUID eventId,
    Long projectId,
    String actorEmail,
    Instant occurredAt,
    Long taskId,
    Long commentId,
    String title,
    String assigneeEmail) {}
