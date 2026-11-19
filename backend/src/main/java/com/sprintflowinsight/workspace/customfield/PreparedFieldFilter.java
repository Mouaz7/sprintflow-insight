package com.sprintflowinsight.workspace.customfield;

import jakarta.persistence.Query;

/** Only server-owned columns/operators enter SQL; every data value is a bound parameter. */
public record PreparedFieldFilter(
    Long projectId, Long fieldId, FieldKind kind, FieldOperator operator, FieldValue expected) {
  public PreparedFieldFilter {
    if (projectId == null
        || projectId < 1
        || fieldId == null
        || fieldId < 1
        || kind == null
        || operator == null
        || !operator.supports(kind)
        || operator.needsValue() != (expected != null))
      throw new IllegalArgumentException("A validated project field condition is required.");
  }

  /** The task alias is deliberately fixed in board, search and export queries. */
  public String taskPredicate() {
    String scope =
        """
        select 1 from task_custom_value custom_value
        where custom_value.task_id=task.id and custom_value.project_id=task.project_id
          and custom_value.project_id=:customProjectId and custom_value.field_id=:customFieldId
        """;
    if (operator == FieldOperator.IS_NOT_SET) return "not exists (" + scope + ")";
    if (operator == FieldOperator.IS_SET) return "exists (" + scope + ")";
    String column =
        switch (kind) {
          case TEXT, SELECT -> "custom_value.text_value";
          case NUMBER -> "custom_value.number_value";
          case DATE -> "custom_value.date_value";
          case USER -> "custom_value.member_id";
        };
    String comparison =
        switch (operator) {
          case CONTAINS -> "position(cast(:customValue as text) in custom_value.text_value)>0";
          case EQUALS -> column + " = :customValue";
          case NOT_EQUALS -> column + " <> :customValue";
          case LESS_THAN -> column + " < :customValue";
          case AT_MOST -> column + " <= :customValue";
          case GREATER_THAN -> column + " > :customValue";
          case AT_LEAST -> column + " >= :customValue";
          default -> throw new IllegalStateException("Unsupported field condition.");
        };
    return "exists (" + scope + " and " + comparison + ")";
  }

  public void bind(Query query) {
    query.setParameter("customProjectId", projectId).setParameter("customFieldId", fieldId);
    if (expected != null)
      query.setParameter(
          "customValue",
          switch (kind) {
            case TEXT, SELECT -> expected.text();
            case NUMBER -> expected.number();
            case DATE -> expected.date();
            case USER -> expected.memberId();
          });
  }
}
