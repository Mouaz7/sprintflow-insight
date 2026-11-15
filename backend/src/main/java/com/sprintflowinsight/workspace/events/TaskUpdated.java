package com.sprintflowinsight.workspace.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/**
 * A task changed without changing its status (status changes publish {@link TaskMoved}), for
 * example an edit, block/unblock, a sprint change through its story, or a restore from the recycle
 * bin. {@code version} is the task's version after the change.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskUpdated(
    UUID eventId,
    Long projectId,
    Long actorUserId,
    Instant occurredAt,
    Long taskId,
    Long version) {}
