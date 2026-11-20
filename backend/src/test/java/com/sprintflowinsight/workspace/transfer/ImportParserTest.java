package com.sprintflowinsight.workspace.transfer;

import static org.assertj.core.api.Assertions.*;

import com.sprintflowinsight.common.error.ApiException;
import org.junit.jupiter.api.Test;

class ImportParserTest {
  @Test
  void csvHandlesBomQuotesEmbeddedNewlinesAndCrLf() {
    var t =
        CsvImportParser.parse(
            "\ufefftitle,description\r\n\"Hello, world\",\"Line 1\nLine 2 says \"\"hi\"\"\"\r\n");
    assertThat(t.rows().getFirst())
        .containsEntry("title", "Hello, world")
        .containsEntry("description", "Line 1\nLine 2 says \"hi\"");
  }

  @Test
  void malformedCsvAndResourceLimitsAreRejected() {
    for (var s :
        new String[] {
          "",
          "title,title\na,b",
          "title\n\"unclosed",
          "title\n\"closed\"tail",
          "title\na,b",
          "title\n\0",
          "title\n" + "a".repeat(8193),
          "title\n" + "row\n".repeat(201)
        }) assertThatThrownBy(() -> CsvImportParser.parse(s)).isInstanceOf(ApiException.class);
  }

  @Test
  void jiraRejectsAmbiguousDocumentsAndKeepsOnlyExplicitAssigneeEmail() {
    for (var s :
        new String[] {
          "[]",
          "{}",
          "{\"issues\":[]} trailing",
          "[{\"fields\":{},\"fields\":{}}]",
          "[".repeat(40) + "]".repeat(40)
        }) assertThatThrownBy(() -> JiraImportParser.parse(s)).isInstanceOf(ApiException.class);
    var t =
        JiraImportParser.parse(
            "[{\"key\":\"DEMO-1\",\"fields\":{\"summary\":\"Task\",\"assignee\":{\"emailAddress\":\"member@example.com\",\"name\":\"private-name\"},\"reporter\":{\"name\":\"private-name\"},\"customfield_123\":9007199254740993,\"description\":{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Plain"
                + " text\"}]}]}}}]");
    assertThat(t.rows().getFirst())
        .containsEntry("fields.assignee.emailAddress", "member@example.com")
        .containsEntry("fields.customfield_123", "9007199254740993")
        .containsEntry("fields.description", "Plain text\n");
    assertThat(t.columns()).doesNotContain("fields.reporter.name", "fields.assignee.name");
  }

  @Test
  void jiraChoosesTheEarliestStartAcrossTimeZones() {
    var table =
        JiraImportParser.parse(
            """
[{"key":"DEMO-1","fields":{"summary":"Task"},"changelog":{"histories":[
  {"created":"2026-03-02T10:00:00.000+0000","items":[{"field":"status","toString":"In Progress"}]},
  {"created":"2026-03-02T11:00:00.000+0200","items":[{"field":"status","toString":"In Progress"}]}
]}}]
""");
    assertThat(table.rows().getFirst()).containsEntry("startedAt", "2026-03-02T09:00:00Z");
  }
}
