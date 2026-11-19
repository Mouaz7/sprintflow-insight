package com.sprintflowinsight.workspace.customfield;

import java.util.List;

public final class CustomFieldViews {
  private CustomFieldViews() {}

  public record Definition(
      Long id, Long projectId, String name, FieldKind kind, List<String> options, Long version) {
    public Definition {
      options = List.copyOf(options);
    }

    static Definition of(CustomField field) {
      return new Definition(
          field.id(),
          field.projectId(),
          field.name(),
          field.kind(),
          field.options(),
          field.version());
    }
  }

  /**
   * USER values are membership ids; display labels follow current membership, never saved emails.
   */
  public record Value(Long fieldId, String value, String displayValue) {}

  public record TaskValues(Long taskId, Long version, List<Value> values) {
    public TaskValues {
      values = List.copyOf(values);
    }
  }

  public record Matches(List<Long> taskIds) {
    public Matches {
      taskIds = List.copyOf(taskIds);
    }
  }

  public record TaskValueRow(Long taskId, Value value) {}
}
