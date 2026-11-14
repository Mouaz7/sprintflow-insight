package com.sprintflowinsight.properties;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.CsvCell;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** CSV export cells: lossless for any text, and never interpreted as a spreadsheet formula. */
class CsvCellProperties {
  @Property(tries = 2000)
  void anyTextRoundTripsThroughRfc4180Quoting(@ForAll("cellText") String text) {
    String quoted = CsvCell.quote(text);
    assertThat(quoted).startsWith("\"").endsWith("\"");
    String unquoted = quoted.substring(1, quoted.length() - 1).replace("\"\"", "\"");
    // The only permitted change is the neutralising apostrophe in front of a formula.
    assertThat(unquoted.equals(text) || unquoted.equals("'" + text)).isTrue();
    // Inside the quotes every quote character is doubled, so the cell cannot end early.
    assertThat(quoted.substring(1, quoted.length() - 1).replace("\"\"", "")).doesNotContain("\"");
  }

  @Property(tries = 2000)
  void textThatASpreadsheetWouldEvaluateIsAlwaysNeutralised(
      @ForAll("invisiblePrefix") String prefix,
      @ForAll("formulaStart") char trigger,
      @ForAll("cellText") String rest) {
    String quoted = CsvCell.quote(prefix + trigger + rest);
    assertThat(quoted).startsWith("\"'");
  }

  @Provide
  Arbitrary<String> cellText() {
    return Arbitraries.strings()
        .withCharRange(' ', '~')
        .withChars('\n', '\r', '\t', '"', ',', 'å', 'ä', 'ö', '﻿')
        .ofMaxLength(60);
  }

  @Provide
  Arbitrary<String> invisiblePrefix() {
    return Arbitraries.strings().withChars(' ', '\t', '\r', '\n', '\u0000', '﻿').ofMaxLength(5);
  }

  @Provide
  Arbitrary<Character> formulaStart() {
    return Arbitraries.of('=', '+', '-', '@');
  }
}
