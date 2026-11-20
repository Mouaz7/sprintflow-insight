package com.sprintflowinsight.workspace.dashboard;

import java.util.List;

/** Version zero denotes the unsaved default; saved revisions start at one. */
public record DashboardResponse(long version, List<DashboardWidget> widgets) {}
