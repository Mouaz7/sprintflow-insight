package com.sprintflowinsight.workspace.roadmap;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** The fields a client may set on an epic. */
public record EpicRequest(
    @NotBlank(message = "Epic title is required.")
        @Size(max = 120, message = "Epic title can be at most 120 characters.")
        String title,
    @Size(max = 2000, message = "Description can be at most 2000 characters.") String description,
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Colour must be a hex value such as #4f46e5.")
        String color,
    LocalDate startDate,
    LocalDate targetDate) {

  public static final String DEFAULT_COLOR = "#4f46e5";

  @AssertTrue(message = "Epic dates must use a year between 1900 and 9999.")
  public boolean isDateRangeValid() {
    return validYear(startDate) && validYear(targetDate);
  }

  private static boolean validYear(LocalDate date) {
    return date == null || (date.getYear() >= 1900 && date.getYear() <= 9999);
  }

  @AssertTrue(message = "The target date cannot be before the start date.")
  public boolean isDateOrderValid() {
    return startDate == null || targetDate == null || !targetDate.isBefore(startDate);
  }

  void applyTo(Epic epic) {
    epic.setTitle(title.trim());
    epic.setDescription(description == null || description.isBlank() ? null : description.trim());
    epic.setColor(color == null ? DEFAULT_COLOR : color.toLowerCase(java.util.Locale.ROOT));
    epic.setStartDate(startDate);
    epic.setTargetDate(targetDate);
  }
}
