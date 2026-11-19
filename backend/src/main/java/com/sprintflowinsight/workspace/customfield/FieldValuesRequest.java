package com.sprintflowinsight.workspace.customfield;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Complete snapshot replacement; omitted/blank values are unset, with If-Match on the task. */
public record FieldValuesRequest(
    @NotNull @Size(max = FieldDefinitionRequest.MAX_FIELDS) List<@Valid Entry> values) {
  public record Entry(
      @NotNull @Positive Long fieldId, @Size(max = FieldValue.MAX_TEXT) String value) {}
}
