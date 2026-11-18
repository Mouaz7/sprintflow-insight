package com.sprintflowinsight.workspace.customfield;

public enum FieldOperator {
  EQUALS,
  NOT_EQUALS,
  CONTAINS,
  LESS_THAN,
  AT_MOST,
  GREATER_THAN,
  AT_LEAST,
  IS_SET,
  IS_NOT_SET;

  public boolean needsValue() {
    return this != IS_SET && this != IS_NOT_SET;
  }

  public boolean supports(FieldKind kind) {
    return switch (this) {
      case CONTAINS -> kind == FieldKind.TEXT;
      case LESS_THAN, AT_MOST, GREATER_THAN, AT_LEAST ->
          kind == FieldKind.NUMBER || kind == FieldKind.DATE;
      default -> true;
    };
  }
}
