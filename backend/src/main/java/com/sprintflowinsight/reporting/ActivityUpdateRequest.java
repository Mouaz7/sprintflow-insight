package com.sprintflowinsight.reporting;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivityUpdateRequest(
    @NotBlank(message = "Update summary is required.")
        @Size(max = 500, message = "Update summary must be 500 characters or fewer.")
        String summary) {}
