package com.sprintflowinsight.workspace.dashboard;

import jakarta.validation.constraints.NotNull;

public record DashboardWidget(@NotNull WidgetType type, @NotNull Boolean wide) {}
