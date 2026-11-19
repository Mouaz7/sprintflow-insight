package com.sprintflowinsight.workspace.customfield;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprintflowinsight.common.error.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

class FieldConditionTest {
  @Test
  void decimalsPreserveDigitsBeyondJavascriptPrecisionAndCompareNumerically() {
    var value = FieldValue.parse(FieldKind.NUMBER, List.of(), "9007199254740993.100000");
    assertThat(value.value()).isEqualTo("9007199254740993.1");
    assertThat(
            new FieldCondition(1L, FieldOperator.GREATER_THAN, "9007199254740993")
                .matches(FieldKind.NUMBER, List.of(), value.value()))
        .isTrue();
    assertThat(
            new FieldCondition(1L, FieldOperator.EQUALS, "1.000000")
                .matches(FieldKind.NUMBER, List.of(), "1"))
        .isTrue();
    assertThat(FieldValue.parse(FieldKind.NUMBER, List.of(), "1e3").value()).isEqualTo("1000");
    assertThat(FieldValue.parse(FieldKind.NUMBER, List.of(), "-0.000").value()).isEqualTo("0");
  }

  @Test
  void extremeExponentsAndOutOfRangeDecimalsAreRejectedBeforePlainStringAllocation() {
    for (String invalid :
        List.of(
            "1e2147483647",
            "10e2147483647",
            "1e-2147483647",
            "1000000000000000000",
            "0.0000001",
            "NaN",
            "Infinity",
            "1,2")) {
      assertThatThrownBy(() -> FieldValue.parse(FieldKind.NUMBER, List.of(), invalid))
          .as(invalid)
          .isInstanceOf(ApiException.class);
    }
    assertThat(FieldValue.parse(FieldKind.NUMBER, List.of(), "-999999999999999999.999999").value())
        .isEqualTo("-999999999999999999.999999");
  }

  @Test
  void missingValuesHaveTheSameExplicitSemanticsForEveryTypeAndOperator() {
    for (FieldKind kind : FieldKind.values()) {
      assertThat(
              new FieldCondition(1L, FieldOperator.IS_NOT_SET, null)
                  .matches(kind, List.of("Ready"), null))
          .isTrue();
      assertThat(
              new FieldCondition(1L, FieldOperator.IS_SET, null)
                  .matches(kind, List.of("Ready"), null))
          .isFalse();
      String expected =
          switch (kind) {
            case DATE -> "2026-11-18";
            case NUMBER, USER -> "1";
            case TEXT, SELECT -> "Ready";
          };
      assertThat(
              new FieldCondition(1L, FieldOperator.NOT_EQUALS, expected)
                  .matches(kind, List.of("Ready"), null))
          .isFalse();
    }
  }

  @Test
  void validatesCalendarDatesAndCurrentSelectOptionsWithoutNormalizingInvalidInput() {
    assertThat(FieldValue.parse(FieldKind.DATE, List.of(), "0001-01-01").value())
        .isEqualTo("0001-01-01");
    assertThat(FieldValue.parse(FieldKind.DATE, List.of(), "2024-02-29").value())
        .isEqualTo("2024-02-29");
    for (String invalid : List.of("2026-02-29", "2026-13-01", "0000-01-01", "+10000-01-01"))
      assertThatThrownBy(() -> FieldValue.parse(FieldKind.DATE, List.of(), invalid))
          .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> FieldValue.parse(FieldKind.SELECT, List.of("Ready"), "ready"))
        .isInstanceOf(ApiException.class);
    assertThat(FieldValue.parse(FieldKind.SELECT, List.of("Ready"), "Ready").value())
        .isEqualTo("Ready");
  }

  @Test
  void textContainsUsesLiteralCharactersAndUserValuesRequirePositiveMembershipIds() {
    assertThat(
            new FieldCondition(1L, FieldOperator.CONTAINS, "%_Ö")
                .matches(FieldKind.TEXT, List.of(), "Literal %_Ö text"))
        .isTrue();
    for (String invalid : List.of("0", "-1", "1.0", "99999999999999999999"))
      assertThatThrownBy(() -> FieldValue.parse(FieldKind.USER, List.of(), invalid))
          .isInstanceOf(ApiException.class);
    assertThat(FieldValue.parse(FieldKind.USER, List.of(), "42").memberId()).isEqualTo(42);
    assertThat(FieldValue.parse(FieldKind.TEXT, List.of(), "  ")).isNull();
    assertThatThrownBy(() -> FieldValue.parse(FieldKind.TEXT, List.of(), "x".repeat(2001)))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void operatorsAndValuesMustAgreeWithTheirFieldType() {
    assertThatThrownBy(
            () ->
                new FieldCondition(1L, FieldOperator.CONTAINS, "1")
                    .expected(FieldKind.NUMBER, List.of()))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                new FieldCondition(1L, FieldOperator.IS_SET, "1")
                    .expected(FieldKind.NUMBER, List.of()))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                new FieldCondition(1L, FieldOperator.EQUALS, null)
                    .expected(FieldKind.TEXT, List.of()))
        .isInstanceOf(ApiException.class);
    assertThat(
            new FieldCondition(1L, FieldOperator.AT_LEAST, "2026-11-18")
                .matches(FieldKind.DATE, List.of(), "2026-11-18"))
        .isTrue();
    assertThat(
            new FieldCondition(1L, FieldOperator.LESS_THAN, "2")
                .matches(FieldKind.NUMBER, List.of(), "1"))
        .isTrue();
  }

  @Test
  void everyOrderedComparisonMatchesAnIndependentTruthTable() {
    var operators =
        List.of(
            FieldOperator.EQUALS,
            FieldOperator.NOT_EQUALS,
            FieldOperator.LESS_THAN,
            FieldOperator.AT_MOST,
            FieldOperator.GREATER_THAN,
            FieldOperator.AT_LEAST);
    boolean[][] expected = {
      {false, true, false},
      {true, false, true},
      {true, false, false},
      {true, true, false},
      {false, false, true},
      {false, true, true}
    };
    for (FieldKind kind : List.of(FieldKind.NUMBER, FieldKind.DATE)) {
      var actual =
          kind == FieldKind.NUMBER
              ? List.of("1", "2", "3")
              : List.of("2026-11-17", "2026-11-18", "2026-11-19");
      for (int o = 0; o < operators.size(); o++)
        for (int a = 0; a < actual.size(); a++)
          assertThat(
                  new FieldCondition(1L, operators.get(o), actual.get(1))
                      .matches(kind, List.of(), actual.get(a)))
              .as("%s %s %s", kind, operators.get(o), actual.get(a))
              .isEqualTo(expected[o][a]);
    }
    for (FieldKind kind : List.of(FieldKind.TEXT, FieldKind.SELECT, FieldKind.USER)) {
      String value = kind == FieldKind.USER ? "42" : "Ready",
          other = kind == FieldKind.USER ? "43" : "Waiting";
      assertThat(
              new FieldCondition(1L, FieldOperator.EQUALS, value)
                  .matches(kind, List.of(value, other), value))
          .isTrue();
      assertThat(
              new FieldCondition(1L, FieldOperator.EQUALS, value)
                  .matches(kind, List.of(value, other), other))
          .isFalse();
      assertThat(
              new FieldCondition(1L, FieldOperator.NOT_EQUALS, value)
                  .matches(kind, List.of(value, other), other))
          .isTrue();
      assertThat(
              new FieldCondition(1L, FieldOperator.IS_SET, null)
                  .matches(kind, List.of(value), value))
          .isTrue();
      assertThat(
              new FieldCondition(1L, FieldOperator.IS_NOT_SET, null)
                  .matches(kind, List.of(value), value))
          .isFalse();
    }
    assertThat(
            new FieldCondition(1L, FieldOperator.CONTAINS, "Ready")
                .matches(FieldKind.TEXT, List.of(), "ready"))
        .isFalse();
    assertThat(
            new FieldCondition(1L, FieldOperator.EQUALS, "x")
                .matches(FieldKind.TEXT, List.of(), " "))
        .isFalse();
  }

  @Test
  void rejectsIncompleteConditionsAndChecksEveryOperatorTypeAndOperandBoundary() {
    assertThat(FieldCondition.optional(null, null, null)).isNull();
    assertThat(FieldCondition.optional(null, null, "")).isNull();
    for (var condition :
        List.of(
            new FieldCondition(null, FieldOperator.IS_SET, null),
            new FieldCondition(0L, FieldOperator.IS_SET, null),
            new FieldCondition(-1L, FieldOperator.IS_SET, null),
            new FieldCondition(1L, null, null),
            new FieldCondition(1L, FieldOperator.EQUALS, "x".repeat(201))))
      assertThatThrownBy(() -> condition.expected(FieldKind.TEXT, List.of()))
          .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> FieldCondition.optional(null, null, "x"))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> FieldCondition.optional(1L, null, null))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> FieldCondition.optional(null, FieldOperator.IS_SET, null))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () -> new FieldCondition(1L, FieldOperator.IS_SET, null).expected(null, List.of()))
        .isInstanceOf(ApiException.class);
    assertThat(FieldCondition.optional(1L, FieldOperator.EQUALS, "x").value()).isEqualTo("x");
    assertThat(
            new FieldCondition(1L, FieldOperator.EQUALS, "x".repeat(200))
                .expected(FieldKind.TEXT, List.of())
                .value())
        .hasSize(200);
    for (FieldOperator op : FieldOperator.values())
      for (FieldKind kind : FieldKind.values()) {
        boolean ordered =
            List.of(
                    FieldOperator.LESS_THAN,
                    FieldOperator.AT_MOST,
                    FieldOperator.GREATER_THAN,
                    FieldOperator.AT_LEAST)
                .contains(op);
        assertThat(op.supports(kind))
            .isEqualTo(
                op == FieldOperator.CONTAINS
                    ? kind == FieldKind.TEXT
                    : !ordered || kind == FieldKind.NUMBER || kind == FieldKind.DATE);
        assertThat(op.needsValue())
            .isEqualTo(op != FieldOperator.IS_SET && op != FieldOperator.IS_NOT_SET);
      }
    assertThat(FieldValue.parse(FieldKind.TEXT, List.of(), "x".repeat(2000)).value()).hasSize(2000);
    assertThat(FieldValue.parse(FieldKind.TEXT, List.of(), " x ").text()).isEqualTo("x");
    assertThatThrownBy(() -> FieldValue.parse(FieldKind.TEXT, List.of(), "a\0b"))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> FieldValue.parse(null, List.of(), "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> FieldValue.parse(FieldKind.SELECT, null, "x"))
        .isInstanceOf(ApiException.class);
    assertThat(FieldValue.parse(FieldKind.NUMBER, List.of(), "999999999999999999").value())
        .isEqualTo("999999999999999999");
    assertThat(FieldValue.parse(FieldKind.NUMBER, List.of(), "0.000001").value())
        .isEqualTo("0.000001");
  }
}
