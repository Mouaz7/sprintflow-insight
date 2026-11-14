package com.sprintflowinsight;

/** Quotes a CSV text cell and prevents spreadsheet formula evaluation. */
public final class CsvCell {
  private CsvCell() {}

  public static String quote(String value) {
    String text = value == null ? "" : value;
    int index = 0;
    while (index < text.length()
        && (Character.isWhitespace(text.charAt(index))
            || Character.isISOControl(text.charAt(index))
            || text.charAt(index) == '\ufeff')) index++;
    if (index < text.length() && "=+-@".indexOf(text.charAt(index)) >= 0) text = "'" + text;
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }
}
