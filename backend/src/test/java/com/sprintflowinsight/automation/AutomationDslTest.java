package com.sprintflowinsight.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprintflowinsight.automation.AutomationRule.Action;
import com.sprintflowinsight.automation.AutomationRule.ActionKind;
import com.sprintflowinsight.automation.AutomationRule.Comparison;
import com.sprintflowinsight.automation.AutomationRule.CustomField;
import com.sprintflowinsight.automation.AutomationRule.HasLabel;
import com.sprintflowinsight.automation.AutomationRule.Trigger;
import com.sprintflowinsight.automation.AutomationRule.TriggerKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AutomationDslTest {
  @Test
  void parsesAMoveRuleWithCustomDataAndEscapedText() {
    var rule =
        AutomationDsl.parse(
            """
            when task-moved from "In Progress" to "Review"
            if label "needs-review"
            if field 42 greater-than "3"
            then comment "Ready for \\"granskning\\".\\nPlease check."
            """);
    assertThat(rule.trigger())
        .isEqualTo(new Trigger(TriggerKind.TASK_MOVED, "In Progress", "Review"));
    assertThat(rule.conditions())
        .containsExactly(
            new HasLabel("needs-review"), new CustomField(42, Comparison.GREATER_THAN, "3"));
    assertThat(rule.action())
        .isEqualTo(new Action(ActionKind.COMMENT, "Ready for \"granskning\".\nPlease check."));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "when overdue\nthen notify \"Follow up\"",
        "when pull-request-merged\nthen move \"Done\"",
        "when task-moved to \"Review\"\nthen assign \"reviewer@example.test\"",
        "when task-moved\nthen label \"needs-review\""
      })
  void supportedRulesParse(String source) {
    assertThat(AutomationDsl.parse(source).action().value()).isNotBlank();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "then move \"Done\"",
        "when overdue",
        "whenOverdue\nthen notify \"Hi\"",
        "when overdue\nthen move \"Unknown\"",
        "when overdue\nthen label \"oops",
        "when overdue\nthen notify \"ok\"then",
        "when task-moved\nif field -1 equals \"hi\"\nthen label \"ok\"",
        "when overdue\nthen notify \"note\"\nthen label \"again\"",
        "when overdue\nthen notify \"cannot \\q\""
      })
  void invalidLanguageReportsTheLine(String source) {
    assertThatThrownBy(() -> AutomationDsl.parse(source))
        .isInstanceOfSatisfying(
            AutomationDsl.ParseException.class, error -> assertThat(error.line()).isPositive());
  }
}
