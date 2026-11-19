package com.sprintflowinsight.notifications.digest;

import java.time.Instant;

public record DigestResponse(
    DigestFrequency frequency,
    String timeZone,
    Instant nextDueAt,
    Instant lastSentAt,
    long version,
    boolean mailConfigured) {}
