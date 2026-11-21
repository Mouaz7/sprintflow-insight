package com.sprintflowinsight.workspace.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Users are referenced by id; see {@link NotificationRequested}. {@code version} is the task's
 * version after the move; it is {@code null} in publications stored before B1.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskMoved(
    UUID eventId,
    Long projectId,
    Long actorUserId,
    Instant occurredAt,
    Long taskId,
    String title,
    String previousStatus,
    String status,
    Long version,
    List<Long> automationTrail) {
  public TaskMoved {
    automationTrail = automationTrail == null ? List.of() : List.copyOf(automationTrail);
  }

  /** Manual moves and callers compiled before automation have no prior rule in their chain. */
  public TaskMoved(
      UUID eventId,
      Long projectId,
      Long actorUserId,
      Instant occurredAt,
      Long taskId,
      String title,
      String previousStatus,
      String status,
      Long version) {
    this(
        eventId,
        projectId,
        actorUserId,
        occurredAt,
        taskId,
        title,
        previousStatus,
        status,
        version,
        List.of());
  }
}
