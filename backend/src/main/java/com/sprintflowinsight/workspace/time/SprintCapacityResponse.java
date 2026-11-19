package com.sprintflowinsight.workspace.time;

import java.time.LocalDate;
import java.util.List;

public record SprintCapacityResponse(
    Long sprintId,
    String sprintName,
    LocalDate startDate,
    LocalDate endDate,
    int workingDays,
    List<MemberCapacityResponse> members,
    TeamCapacity team,
    List<CapacityWarning> warnings) {}
