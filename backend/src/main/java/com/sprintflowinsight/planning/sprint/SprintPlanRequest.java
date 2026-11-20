package com.sprintflowinsight.planning.sprint;

import com.sprintflowinsight.workspace.time.CapacityUnit;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** A preview scenario. Overrides are effective capacity for this request only. */
public record SprintPlanRequest(
    @NotNull @Positive Long sprintId,
    @NotNull CapacityUnit unit,
    @Min(0) @Max(1_000_000) Integer totalCapacity,
    @NotNull @Size(max = 100)
        Map<@NotNull @Positive Long, @NotNull @Min(0) @Max(1_000_000) Integer> memberCapacities,
    @NotNull @Size(max = 200) List<@NotNull @Positive Long> lockedIn,
    @NotNull @Size(max = 200) List<@NotNull @Positive Long> lockedOut) {}
