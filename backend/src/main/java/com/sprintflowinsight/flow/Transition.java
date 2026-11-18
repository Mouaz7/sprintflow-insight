package com.sprintflowinsight.flow;

import java.time.Instant;
import java.util.Objects;

/** A task entering {@code status} at {@code at}. */
public record Transition(String status, Instant at) {
  public Transition {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(at, "at");
  }
}
