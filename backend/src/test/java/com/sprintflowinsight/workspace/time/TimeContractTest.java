package com.sprintflowinsight.workspace.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class TimeContractTest {
  @Test
  @SuppressWarnings("unchecked")
  void documentsAllTimeAndCapacityRecordsAndConcurrencyHeaders() throws Exception {
    try (var input = Files.newInputStream(Path.of("..", "docs", "openapi.yaml"))) {
      Map<String, Object> doc = new Yaml().load(input);
      var schemas =
          (Map<String, Map<String, Object>>) ((Map<?, ?>) doc.get("components")).get("schemas");
      var types =
          Map.ofEntries(
              Map.entry("TimeEntry", TimeEntryResponse.class),
              Map.entry("TimeEntryPage", TimeEntryPage.class),
              Map.entry("TimeReport", TimeReport.class),
              Map.entry("TimeReportRow", TimeReportRow.class),
              Map.entry("SprintCapacity", SprintCapacityResponse.class),
              Map.entry("MemberCapacity", MemberCapacityResponse.class),
              Map.entry("TeamCapacity", TeamCapacity.class),
              Map.entry("CapacityWarning", CapacityWarning.class),
              Map.entry("SprintCapacityRequest", CapacityRequest.class),
              Map.entry("TimeEntryRequest", TimeEntryRequest.class),
              Map.entry("TimeEntryUpdateRequest", TimeEntryUpdateRequest.class),
              Map.entry("TimerStartRequest", TimerStartRequest.class),
              Map.entry("TimeEstimateRequest", EstimateRequest.class),
              Map.entry("TaskTimeEstimate", TaskEstimateResponse.class));
      types.forEach(
          (name, type) ->
              assertThat(((Map<String, Object>) schemas.get(name).get("properties")).keySet())
                  .as(name)
                  .containsExactlyInAnyOrderElementsOf(
                      Arrays.stream(type.getRecordComponents())
                          .map(java.lang.reflect.RecordComponent::getName)
                          .toList()));
      var properties =
          (Map<String, Map<String, Object>>) schemas.get("CapacityWarning").get("properties");
      assertThat((java.util.List<String>) properties.get("code").get("enum"))
          .containsExactlyElementsOf(
              Arrays.stream(CapacityWarning.Code.values()).map(Enum::name).toList());
      var paths = (Map<String, Map<String, Object>>) doc.get("paths");
      String base = "/api/projects/{projectId}";
      for (String suffix :
          java.util.List.of(
              "/time-entries",
              "/time-entries/{entryId}",
              "/timer",
              "/timer/stop",
              "/tasks/{taskId}/time-estimate",
              "/sprints/{sprintId}/capacity",
              "/sprints/{sprintId}/capacity/{userId}",
              "/time-report",
              "/time-report.csv")) assertThat(paths).containsKey(base + suffix);
      var estimate =
          (Map<String, Object>) paths.get(base + "/tasks/{taskId}/time-estimate").get("put");
      assertThat(estimate.get("parameters").toString()).contains("ifMatch");
      assertThat((Map<String, Object>) estimate.get("responses")).containsKeys("412", "428");
      assertThat(paths).containsKey(base + "/tasks/{taskId}/estimate");
    }
  }
}
