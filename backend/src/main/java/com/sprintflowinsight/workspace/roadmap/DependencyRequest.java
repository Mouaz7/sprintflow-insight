package com.sprintflowinsight.workspace.roadmap;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** {@code blockerTaskId} must be finished before {@code blockedTaskId}. */
public record DependencyRequest(
    @NotNull(message = "Choose the blocking task.") @Positive Long blockerTaskId,
    @NotNull(message = "Choose the blocked task.") @Positive Long blockedTaskId) {}
