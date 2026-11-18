package com.sprintflowinsight.workspace.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/**
 * A planning poker session changed: started, a vote was cast or retracted, revealed, revoted,
 * accepted or cancelled. It deliberately carries no card, vote count or voter: the event is stored
 * in the publication registry and may be pushed to every project member, while votes must stay
 * secret until the reveal. Clients reload the session through the REST API. {@code version} is the
 * session's version after the change.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PokerSessionChanged(
    UUID eventId,
    Long projectId,
    Long actorUserId,
    Instant occurredAt,
    Long sessionId,
    Long version) {}
