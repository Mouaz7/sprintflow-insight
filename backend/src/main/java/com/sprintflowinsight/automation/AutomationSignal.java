package com.sprintflowinsight.automation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record AutomationSignal(
    UUID eventId,
    long projectId,
    long taskId,
    AutomationRule.TriggerKind kind,
    String from,
    String to,
    Instant dueAt,
    List<Long> trail) {
  AutomationSignal {
    trail = List.copyOf(trail);
  }
}
