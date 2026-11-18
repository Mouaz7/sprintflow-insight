package com.sprintflowinsight.reporting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Replaces all WIP limits of a project. Columns left out have no limit; an empty list removes
 * every limit.
 */
public record WipLimitsRequest(@NotNull @Size(max = 3) List<@NotNull @Valid Entry> limits) {

  /** One column limit. Done cannot be limited. */
  public record Entry(
      @NotNull @Pattern(regexp = "To Do|In Progress|Review") String status,
      @NotNull @Min(1) @Max(999) Integer limit) {}
}
