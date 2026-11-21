package com.sprintflowinsight.automation;

import com.sprintflowinsight.automation.AutomationRule.Action;
import com.sprintflowinsight.automation.AutomationRule.ActionKind;
import com.sprintflowinsight.automation.AutomationRule.Comparison;
import com.sprintflowinsight.automation.AutomationRule.Condition;
import com.sprintflowinsight.automation.AutomationRule.CustomField;
import com.sprintflowinsight.automation.AutomationRule.HasLabel;
import com.sprintflowinsight.automation.AutomationRule.StatusIs;
import com.sprintflowinsight.automation.AutomationRule.Trigger;
import com.sprintflowinsight.automation.AutomationRule.TriggerKind;
import java.util.ArrayList;
import java.util.Set;

/** Line-based rules with quoted values. Parsing performs no I/O and executes no actions. */
public final class AutomationDsl {
  public static final int MAX_SOURCE_LENGTH = 4_096;
  public static final int MAX_CONDITIONS = 5;
  private static final Set<String> STATUSES = Set.of("To Do", "In Progress", "Review", "Done");

  private AutomationDsl() {}

  public static AutomationRule parse(String source) {
    if (source == null || source.length() > MAX_SOURCE_LENGTH)
      throw new ParseException(1, "Rule text is limited to 4096 characters.");
    var lines = source.lines().toList();
    Trigger trigger = null;
    Action action = null;
    var conditions = new ArrayList<Condition>();
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).isBlank()) continue;
      var line = new Line(lines.get(i), i + 1);
      if (trigger == null) {
        line.require("when");
        trigger = trigger(line);
      } else if (action != null) {
        throw line.error("The rule ends after its action.");
      } else if (line.take("if")) {
        if (conditions.size() == MAX_CONDITIONS) throw line.error("Use at most five conditions.");
        conditions.add(condition(line));
      } else {
        line.require("then");
        action = action(line);
      }
      line.end();
    }
    if (trigger == null || action == null)
      throw new ParseException(
          Math.max(1, lines.size()), "Use one 'when' and one 'then' statement.");
    return new AutomationRule(trigger, conditions, action);
  }

  private static Trigger trigger(Line line) {
    return switch (line.word()) {
      case "task-moved" ->
          new Trigger(
              TriggerKind.TASK_MOVED,
              line.take("from") ? line.status() : null,
              line.take("to") ? line.status() : null);
      case "pull-request-merged" -> new Trigger(TriggerKind.PULL_REQUEST_MERGED, null, null);
      case "overdue" -> new Trigger(TriggerKind.OVERDUE, null, null);
      default -> throw line.error("Use task-moved, pull-request-merged, or overdue.");
    };
  }

  private static Condition condition(Line line) {
    return switch (line.word()) {
      case "status" -> new StatusIs(line.status());
      case "label" -> new HasLabel(line.value(32));
      case "field" -> {
        long id = line.fieldId();
        var operator = comparison(line);
        String operand =
            switch (operator) {
              case IS_SET, IS_NOT_SET -> null;
              default -> line.value(200);
            };
        yield new CustomField(id, operator, operand);
      }
      default -> throw line.error("Use status, label, or field for a condition.");
    };
  }

  private static Comparison comparison(Line line) {
    return switch (line.word()) {
      case "equals" -> Comparison.EQUALS;
      case "not-equals" -> Comparison.NOT_EQUALS;
      case "contains" -> Comparison.CONTAINS;
      case "greater-than" -> Comparison.GREATER_THAN;
      case "less-than" -> Comparison.LESS_THAN;
      case "at-most" -> Comparison.AT_MOST;
      case "at-least" -> Comparison.AT_LEAST;
      case "is-set" -> Comparison.IS_SET;
      case "is-not-set" -> Comparison.IS_NOT_SET;
      default -> throw line.error("Choose a field comparison.");
    };
  }

  private static Action action(Line line) {
    return switch (line.word()) {
      case "assign" -> new Action(ActionKind.ASSIGN, line.value(255));
      case "label" -> new Action(ActionKind.LABEL, line.value(32));
      case "move" -> new Action(ActionKind.MOVE, line.status());
      case "comment" -> new Action(ActionKind.COMMENT, line.value(1000));
      case "notify" -> new Action(ActionKind.NOTIFY, line.value(1000));
      default -> throw line.error("Use assign, label, move, comment, or notify.");
    };
  }

  public static final class ParseException extends IllegalArgumentException {
    private final int line;

    ParseException(int line, String description) {
      super(description);
      this.line = line;
    }

    public int line() {
      return line;
    }
  }

  private static final class Line {
    private final String text;
    private final int number;
    private int position;

    Line(String text, int number) {
      this.text = text;
      this.number = number;
    }

    private void skipSpace() {
      while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++;
    }

    boolean take(String token) {
      skipSpace();
      int after = position + token.length();
      if (!text.startsWith(token, position)
          || (after < text.length() && !Character.isWhitespace(text.charAt(after)))) return false;
      position = after;
      return true;
    }

    void require(String token) {
      if (!take(token)) throw error("Expected '" + token + "'.");
    }

    String word() {
      skipSpace();
      int start = position;
      while (position < text.length() && !Character.isWhitespace(text.charAt(position))) position++;
      if (position == start) throw error("Expected a keyword.");
      return text.substring(start, position);
    }

    long fieldId() {
      String value = word();
      try {
        long id = Long.parseLong(value);
        if (id > 0) return id;
      } catch (NumberFormatException ignored) {
        // A missing or out-of-range identifier is reported using the same grammar error.
      }
      throw error("Use a positive custom field ID.");
    }

    String status() {
      String status = value(11);
      if (!STATUSES.contains(status)) throw error("Choose To Do, In Progress, Review, or Done.");
      return status;
    }

    String value(int maxLength) {
      skipSpace();
      if (position >= text.length() || text.charAt(position++) != '"')
        throw error("Put values in double quotes.");
      var value = new StringBuilder();
      while (position < text.length()) {
        char current = text.charAt(position++);
        if (current == '"') {
          String result = value.toString();
          if (result.isBlank()) throw error("Use a non-empty value.");
          if (position < text.length() && !Character.isWhitespace(text.charAt(position)))
            throw error("Separate quoted values and keywords with spaces.");
          return result;
        }
        if (current == '\\') current = escaped();
        else if (Character.isISOControl(current)) throw error("Use printable text in values.");
        if (value.length() >= maxLength) throw error("This value is too long.");
        value.append(current);
      }
      throw error("Close the quoted value.");
    }

    private char escaped() {
      if (position == text.length()) throw error("Complete the escape in the quoted value.");
      return switch (text.charAt(position++)) {
        case '"' -> '"';
        case '\\' -> '\\';
        case 'n' -> '\n';
        case 't' -> '\t';
        default -> throw error("Use a supported quoted-value escape.");
      };
    }

    void end() {
      skipSpace();
      if (position != text.length()) throw error("Unexpected text after this statement.");
    }

    ParseException error(String description) {
      return new ParseException(number, description);
    }
  }
}
