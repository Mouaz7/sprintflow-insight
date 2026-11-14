package com.sprintflowinsight.auth;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial preference update: a {@code null} field leaves the stored value unchanged.
 *
 * @param savedFilters JSON array of saved board filters, kept as text on the wire for client
 *     compatibility and validated structurally by {@link SavedFilter}
 */
public record PreferencesRequest(
    @Pattern(regexp = "EN|SV", message = "Language must be EN or SV.") String language,
    @Pattern(regexp = "light|dark", message = "Theme must be light or dark.") String theme,
    Boolean notifications,
    Boolean onboardingCompleted,
    @Size(max = 30_000, message = "Saved filters are too large.") String savedFilters) {

  /** Structural contract for one saved filter; other properties are stored untouched. */
  public record SavedFilter(
      @jakarta.validation.constraints.NotBlank String id,
      @jakarta.validation.constraints.NotBlank
          @Size(max = 80, message = "Filter names can be at most 80 characters.")
          String name) {}
}
