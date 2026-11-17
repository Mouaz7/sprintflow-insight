package com.sprintflowinsight.realtime;

import jakarta.validation.constraints.Positive;

/** Null closes the currently viewed task. */
public record OpenTaskRequest(@Positive Long taskId) {}
