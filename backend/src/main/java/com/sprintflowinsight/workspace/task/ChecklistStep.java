package com.sprintflowinsight.workspace.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChecklistStep(
    @NotBlank @Size(max = 80) String id,
    @NotBlank @Size(max = 180) String label,
    boolean completed,
    boolean required) {}
