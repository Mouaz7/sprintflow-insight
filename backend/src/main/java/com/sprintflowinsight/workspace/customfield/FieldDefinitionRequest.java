package com.sprintflowinsight.workspace.customfield;

import com.sprintflowinsight.common.error.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record FieldDefinitionRequest(
    @NotBlank @Size(max = 80) String name,
    @NotNull FieldKind kind,
    @Size(max = 30) List<@NotBlank @Size(max = 80) String> options) {
  public static final int MAX_FIELDS = 20;

  String normalizedName() {
    if (name == null || name.isBlank() || name.length() > 80 || name.indexOf('\0') >= 0)
      throw ApiException.badRequest("Use a field name of 1–80 characters.");
    return name.strip();
  }

  List<String> normalizedOptions() {
    if (kind == null) throw ApiException.badRequest("Choose a field type.");
    var source = options == null ? List.<String>of() : options;
    if (source.size() > 30
        || source.stream()
            .anyMatch(
                item ->
                    item == null
                        || item.isBlank()
                        || item.length() > 80
                        || item.indexOf('\0') >= 0))
      throw ApiException.badRequest("Use at most 30 options of 1–80 characters each.");
    var normalized = source.stream().map(String::strip).toList();
    if (normalized.stream().distinct().count() != normalized.size())
      throw ApiException.badRequest("Field options must be unique.");
    if (kind == FieldKind.SELECT && normalized.isEmpty())
      throw ApiException.badRequest("A select field needs at least one option.");
    if (kind != FieldKind.SELECT && !normalized.isEmpty())
      throw ApiException.badRequest("Only select fields have options.");
    return normalized;
  }
}
