package com.sprintflowinsight.workspace.transfer;

import static org.assertj.core.api.Assertions.*;

import com.code_intelligence.jazzer.junit.FuzzTest;
import com.sprintflowinsight.common.error.ApiException;
import java.nio.charset.StandardCharsets;

/** Run with JAZZER_FUZZ=1; ordinary test runs replay the checked-in seed corpora. */
class ImportParserFuzzTest {
  @FuzzTest(maxDuration = "30s")
  void csv(byte[] data) {
    try {
      var table = CsvImportParser.parse(new String(data, StandardCharsets.UTF_8));
      assertThat(table.rows()).hasSizeBetween(1, 200);
      assertThat(table.columns()).hasSizeBetween(1, 64);
      assertThat(table.columns()).doesNotHaveDuplicates();
      for (var row : table.rows()) assertThat(row).hasSize(table.columns().size());
    } catch (ApiException expected) {
      assertThat(expected.getStatusCode().value()).isEqualTo(400);
    }
  }

  @FuzzTest(maxDuration = "30s")
  void jira(byte[] data) {
    try {
      var table = JiraImportParser.parse(new String(data, StandardCharsets.UTF_8));
      assertThat(table.rows()).hasSizeBetween(1, 200);
      assertThat(table.columns()).hasSizeLessThanOrEqualTo(256);
      for (var row : table.rows())
        assertThat(row.keySet())
            .noneMatch(
                k ->
                    k.startsWith("fields.reporter")
                        || k.startsWith("fields.creator")
                        || k.startsWith("fields.comment")
                        || k.startsWith("changelog."));
    } catch (ApiException expected) {
      assertThat(expected.getStatusCode().value()).isEqualTo(400);
    }
  }
}
