/**
 * Realtime updates (B1): STOMP over WebSocket at {@code /ws}, project topics authorised per
 * subscription, per-project sequence numbers with a short replay log for gap filling, and
 * in-memory presence. Messages are produced from durable module events after commit. See ADR 0017.
 */
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
      "auth",
      "notifications",
      "workspace :: task",
      "common",
      "common :: errors",
      "workspace :: project",
      "workspace :: events"
    })
package com.sprintflowinsight.realtime;
