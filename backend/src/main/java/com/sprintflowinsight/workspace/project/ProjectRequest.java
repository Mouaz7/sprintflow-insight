package com.sprintflowinsight.workspace.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The fields a client may set on a project. The owner is always the signed-in user. */
public record ProjectRequest(
    @NotBlank(message = "Project name is required.")
        @Size(max = 255, message = "Project name can be at most 255 characters.")
        String name,
    @Size(max = 255, message = "Description can be at most 255 characters.") String description) {

  public String trimmedName() {
    return name.trim();
  }
}
