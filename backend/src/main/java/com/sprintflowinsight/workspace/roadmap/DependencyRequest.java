package com.sprintflowinsight.workspace.roadmap;

import jakarta.validation.constraints.NotNull;

/** {@code blockerTaskId} must be finished before {@code blockedTaskId}. */
public record DependencyRequest(
    @NotNull(message = "Choose the blocking task.") Long blockerTaskId,
    @NotNull(message = "Choose the blocked task.") Long blockedTaskId) {}
