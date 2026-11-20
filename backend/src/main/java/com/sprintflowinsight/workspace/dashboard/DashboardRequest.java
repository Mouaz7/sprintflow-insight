package com.sprintflowinsight.workspace.dashboard;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DashboardRequest(
    @NotNull @Size(max = 6) List<@NotNull @Valid DashboardWidget> widgets) {}
