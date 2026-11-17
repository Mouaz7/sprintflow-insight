package com.sprintflowinsight.notifications;

import java.time.Instant;
import java.util.UUID;

/**
 * Inbox row persisted in the listener transaction; no message or recipient data leaves this module.
 */
public record NotificationDelivered(UUID eventId, Long projectId, Instant occurredAt) {}
