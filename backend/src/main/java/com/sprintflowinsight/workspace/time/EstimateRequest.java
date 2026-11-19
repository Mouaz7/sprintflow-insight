package com.sprintflowinsight.workspace.time;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * @param estimateMinutes {@code null} removes the estimate
 */
public record EstimateRequest(
    @Min(value = 0, message = "The estimate must be between 0 and 600000 minutes.")
        @Max(value = 600000, message = "The estimate must be between 0 and 600000 minutes.")
        Integer estimateMinutes) {}
