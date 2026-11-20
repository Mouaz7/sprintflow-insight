package com.sprintflowinsight.workspace.time;

import java.util.List;

/**
 * @param nextBefore pass as {@code before} to read the next page; {@code null} on the last
 */
public record TimeEntryPage(List<TimeEntryResponse> items, Long nextBefore) {}
