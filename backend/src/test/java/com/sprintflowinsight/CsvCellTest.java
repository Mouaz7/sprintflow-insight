package com.sprintflowinsight;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsvCellTest {
  @Test
  void neutralizesFormulaPrefixesIncludingWhitespaceAndControls() {
    for (String value :
        new String[] {"=1+1", "+SUM(A1)", "-1+2", "@SUM(A1)", " \t=1", "\r\n@x", "\ufeff=1"})
      assertThat(CsvCell.quote(value)).startsWith("\"'");
  }

  @Test
  void quotesTextWithoutLosingCommasQuotesOrNewlines() {
    assertThat(CsvCell.quote("a,\"b\"\nc")).isEqualTo("\"a,\"\"b\"\"\nc\"");
    assertThat(CsvCell.quote(null)).isEqualTo("\"\"");
    assertThat(CsvCell.quote("ordinary text")).isEqualTo("\"ordinary text\"");
  }
}
