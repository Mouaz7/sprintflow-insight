package com.sprintflowinsight.realtime;

import java.util.List;

/** The full list of people viewing a project, sent to {@code /topic/projects/{id}/presence}. */
public record PresenceMessage(long projectId, List<Viewer> viewers) {
  /** One person, however many tabs they have open. Never includes an email address. */
  public record Viewer(long userId, String name, List<Long> taskIds) {
    public Viewer(long userId, String name) {
      this(userId, name, List.of());
    }
  }
}
