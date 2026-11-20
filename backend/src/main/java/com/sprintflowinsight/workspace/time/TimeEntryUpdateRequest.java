package com.sprintflowinsight.workspace.time;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Correction of a stopped or manual entry. Task and person are fixed once logged. */
public record TimeEntryUpdateRequest(
    @NotNull(message = "Minutes are required.")
        @Min(value = 1, message = "Minutes must be between 1 and 1440.")
        @Max(value = 1440, message = "Minutes must be between 1 and 1440.")
        Integer minutes,
    @NotNull(message = "Choose the day the time was spent.") LocalDate spentOn,
    @Size(max = 500, message = "A note can be at most 500 characters.") String note) {}
