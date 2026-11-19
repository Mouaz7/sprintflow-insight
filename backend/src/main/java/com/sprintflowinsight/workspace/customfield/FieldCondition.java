package com.sprintflowinsight.workspace.customfield;

import com.sprintflowinsight.common.error.ApiException;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The same condition shape and null semantics are available to board/search and B15 rules. */
public record FieldCondition(
    @NotNull @Positive Long fieldId,
    @NotNull FieldOperator operator,
    @Size(max = FieldCondition.MAX_FILTER_VALUE) String value) {
  public static final int MAX_FILTER_VALUE = 200;

  public static FieldCondition optional(Long fieldId, FieldOperator operator, String value) {
    if (fieldId == null && operator == null && (value == null || value.isEmpty())) return null;
    if (fieldId == null || operator == null)
      throw ApiException.badRequest("Choose both a custom field and an operator.");
    return new FieldCondition(fieldId, operator, value);
  }

  public FieldValue expected(FieldKind kind, List<String> options) {
    if (value != null && value.length() > MAX_FILTER_VALUE)
      throw ApiException.badRequest("A filter value can be at most 200 characters.");
    if (fieldId == null
        || fieldId < 1
        || kind == null
        || operator == null
        || !operator.supports(kind))
      throw ApiException.badRequest("Choose a valid field and an operator supported by its type.");
    FieldValue parsed = FieldValue.parse(kind, options, value);
    if (operator.needsValue() != (parsed != null))
      throw ApiException.badRequest("Provide a value only when the selected operator needs one.");
    return parsed;
  }

  /** Missing values match only IS_NOT_SET, including for NOT_EQUALS. */
  public boolean matches(FieldKind kind, List<String> options, String actual) {
    FieldValue expected = expected(kind, options);
    if (operator == FieldOperator.IS_SET) return actual != null;
    if (operator == FieldOperator.IS_NOT_SET) return actual == null;
    if (actual == null) return false;
    FieldValue observed = FieldValue.parse(kind, options, actual);
    if (observed == null) return false;
    if (operator == FieldOperator.CONTAINS) return observed.text().contains(expected.text());
    int compared =
        switch (kind) {
          case NUMBER -> observed.number().compareTo(expected.number());
          case DATE -> observed.date().compareTo(expected.date());
          case USER -> observed.memberId().compareTo(expected.memberId());
          case TEXT, SELECT -> observed.text().compareTo(expected.text());
        };
    return switch (operator) {
      case EQUALS -> compared == 0;
      case NOT_EQUALS -> compared != 0;
      case LESS_THAN -> compared < 0;
      case AT_MOST -> compared <= 0;
      case GREATER_THAN -> compared > 0;
      case AT_LEAST -> compared >= 0;
      default -> throw new IllegalStateException("Unsupported operator.");
    };
  }
}
