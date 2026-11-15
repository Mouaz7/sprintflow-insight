package com.sprintflowinsight.workspace.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The fields a client may set on a project. The owner is always the signed-in user.
 *
 * @param key optional project key (e.g. {@code SF}); derived from the name on create and kept on
 *     update when omitted. Lower case is accepted and stored upper case.
 */
public record ProjectRequest(
    @NotBlank(message = "Project name is required.")
        @Size(max = 255, message = "Project name can be at most 255 characters.")
        String name,
    @Size(max = 255, message = "Description can be at most 255 characters.") String description,
    @Pattern(regexp = KEY_INPUT, message = KEY_MESSAGE) String key) {

  /** Client input for a project key: 2–10 letters A–Z in either case. */
  public static final String KEY_INPUT = "\\s*[A-Za-z]{2,10}\\s*";

  public static final String KEY_MESSAGE = "Project key must be 2–10 letters A–Z.";

  public ProjectRequest(String name, String description) {
    this(name, description, null);
  }

  public String trimmedName() {
    return name.trim();
  }

  /** The requested key in stored form, or {@code null} when none was given. */
  public String normalizedKey() {
    return key == null || key.isBlank() ? null : ProjectKeys.normalize(key);
  }
}
