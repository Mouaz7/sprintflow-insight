package com.sprintflowinsight.workspace.time;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TimerStartRequest(
    @NotNull(message = "Choose a task.") Long taskId,
    @Size(max = 500, message = "A note can be at most 500 characters.") String note) {}
