package com.sprintflowinsight.workspace.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TemplateRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 255) String description,
    @NotBlank String template,
    String language,
    @Pattern(regexp = ProjectRequest.KEY_INPUT, message = ProjectRequest.KEY_MESSAGE)
        String key) {

  public TemplateRequest(String name, String description, String template, String language) {
    this(name, description, template, language, null);
  }

  public String normalizedKey() {
    return key == null || key.isBlank() ? null : ProjectKeys.normalize(key);
  }
}
