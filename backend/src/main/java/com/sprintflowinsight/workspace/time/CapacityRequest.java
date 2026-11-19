package com.sprintflowinsight.workspace.time;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * One member's capacity for one sprint.
 *
 * @param pointCapacity story points for the whole sprint at full availability; {@code null} when
 *     the team plans in hours only
 * @param daysOff days the member is away; days outside the sprint or on weekends are ignored
 */
public record CapacityRequest(
    @NotNull(message = "Working minutes per day are required.")
        @Min(value = 0, message = "Working minutes per day must be between 0 and 1440.")
        @Max(value = 1440, message = "Working minutes per day must be between 0 and 1440.")
        Integer minutesPerDay,
    @NotNull(message = "Availability is required.")
        @Min(value = 0, message = "Availability must be between 0 and 100 percent.")
        @Max(value = 100, message = "Availability must be between 0 and 100 percent.")
        Integer availabilityPercent,
    @Min(value = 0, message = "Point capacity must be between 0 and 10000.")
        @Max(value = 10000, message = "Point capacity must be between 0 and 10000.")
        Integer pointCapacity,
    @Size(max = 366, message = "At most 366 days off can be listed.")
        List<@NotNull(message = "Days off must be dates.") LocalDate> daysOff) {}
