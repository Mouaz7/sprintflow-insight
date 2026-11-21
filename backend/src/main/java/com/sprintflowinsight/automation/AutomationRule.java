package com.sprintflowinsight.automation;

import java.util.List;

/** Parsed project automation. Execution and authorization belong to the application adapter. */
public record AutomationRule(Trigger trigger, List<Condition> conditions, Action action) {
  public AutomationRule {
    conditions = List.copyOf(conditions);
  }

  public enum TriggerKind {
    TASK_MOVED,
    PULL_REQUEST_MERGED,
    OVERDUE
  }

  public record Trigger(TriggerKind kind, String from, String to) {}

  public sealed interface Condition permits StatusIs, HasLabel, CustomField {}

  public record StatusIs(String status) implements Condition {}

  public record HasLabel(String label) implements Condition {}

  public enum Comparison {
    EQUALS,
    NOT_EQUALS,
    CONTAINS,
    GREATER_THAN,
    LESS_THAN,
    AT_MOST,
    AT_LEAST,
    IS_SET,
    IS_NOT_SET
  }

  public record CustomField(long fieldId, Comparison comparison, String operand)
      implements Condition {}

  public enum ActionKind {
    ASSIGN,
    LABEL,
    MOVE,
    COMMENT,
    NOTIFY
  }

  public record Action(ActionKind kind, String value) {}
}
