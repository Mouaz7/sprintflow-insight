package com.sprintflowinsight.workspace.time;

/** The new parent-task revision after an estimate change. */
public record TaskEstimateResponse(Long taskId, Integer estimateMinutes, Long version) {}
