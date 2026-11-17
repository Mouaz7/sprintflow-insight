package com.sprintflowinsight.realtime;

import java.util.List;

/**
 * Answer to {@code GET /api/projects/{id}/events?after=}.
 *
 * @param latestSeq the newest sequence number for the project, 0 before the first event
 * @param resync {@code true} when the requested range is no longer complete (pruned by retention,
 *     larger than one page, or {@code after} is ahead of the server). {@code events} is then empty
 *     and the client reloads the project and continues from {@code latestSeq}.
 */
public record RealtimeEventPage(
    long projectId, long latestSeq, boolean resync, List<RealtimeMessage> events) {}
