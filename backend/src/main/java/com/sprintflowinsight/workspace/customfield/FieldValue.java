package com.sprintflowinsight.workspace.customfield;

import com.sprintflowinsight.common.error.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Canonical values preserve decimal precision across JSON, SQL, filters and future rules. */
public record FieldValue(
    String value, String text, BigDecimal number, LocalDate date, Long memberId) {
  public static final int MAX_TEXT = 2000;

  /** Blank input clears a value; membership authorization is checked by the owning service. */
  public static FieldValue parse(FieldKind kind, List<String> options, String raw) {
    if (kind == null) throw new IllegalArgumentException("A field kind is required.");
    if (raw == null || raw.isBlank()) return null;
    String value = raw.strip();
    if (value.indexOf('\0') >= 0)
      throw ApiException.badRequest("Custom field values cannot contain null characters.");
    if (value.length() > MAX_TEXT)
      throw ApiException.badRequest("A custom field value can be at most 2000 characters.");
    return switch (kind) {
      case TEXT -> new FieldValue(value, value, null, null, null);
      case SELECT -> {
        if (options == null || !options.contains(value))
          throw ApiException.badRequest("Choose one of this field's current options.");
        yield new FieldValue(value, value, null, null, null);
      }
      case NUMBER -> decimal(value);
      case DATE -> date(value);
      case USER -> member(value);
    };
  }

  private static FieldValue decimal(String value) {
    try {
      BigDecimal number = new BigDecimal(value).stripTrailingZeros();
      if (Math.max(0, number.scale()) > 6 || (long) number.precision() - number.scale() > 18)
        throw new NumberFormatException();
      return new FieldValue(number.toPlainString(), null, number, null, null);
    } catch (NumberFormatException | ArithmeticException invalid) {
      throw ApiException.badRequest(
          "Use a decimal number with at most 18 whole digits and 6 decimal places.");
    }
  }

  private static FieldValue date(String value) {
    try {
      if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
        throw new DateTimeParseException("Invalid date", value, 0);
      LocalDate date = LocalDate.parse(value);
      if (date.getYear() < 1) throw new DateTimeParseException("Invalid year", value, 0);
      return new FieldValue(date.toString(), null, null, date, null);
    } catch (DateTimeParseException invalid) {
      throw ApiException.badRequest("Use a real date in YYYY-MM-DD format, with year 0001–9999.");
    }
  }

  private static FieldValue member(String value) {
    try {
      if (!value.matches("[1-9][0-9]*")) throw new NumberFormatException();
      Long id = Long.valueOf(value);
      return new FieldValue(id.toString(), null, null, null, id);
    } catch (NumberFormatException invalid) {
      throw ApiException.badRequest("Choose a current member of this project.");
    }
  }
}
