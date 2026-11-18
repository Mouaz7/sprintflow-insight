package com.sprintflowinsight.reporting;

import java.util.List;

/** Revision 0 is the initial configuration. Clearing the limits still advances the revision. */
public record WipSettingsResponse(long version, List<WipLimitResponse> limits) {}
