package com.sprintflowinsight.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.flow.XmrChart;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class FlowContractTest {
  @Test
  @SuppressWarnings("unchecked")
  void documentsTheExactFlowRecordsSignalsAndConditionalWrite() throws Exception {
    try (var input = Files.newInputStream(Path.of("..", "docs", "openapi.yaml"))) {
      Map<String, Object> spec = new Yaml().load(input);
      var schemas =
          (Map<String, Map<String, Object>>) ((Map<?, ?>) spec.get("components")).get("schemas");
      var records =
          Map.ofEntries(
              Map.entry("FlowQuery", FlowQuery.class),
                  Map.entry("FlowResponse", FlowResponse.class),
              Map.entry("WipLimitsRequest", WipLimitsRequest.class),
                  Map.entry("WipLimitEntryRequest", WipLimitsRequest.Entry.class),
              Map.entry("WipSettingsResponse", WipSettingsResponse.class),
                  Map.entry("WipLimitResponse", WipLimitResponse.class),
              Map.entry("FlowCompletion", FlowResponse.Completion.class),
                  Map.entry("FlowCumulativeDay", FlowResponse.CumulativeFlowDay.class),
              Map.entry("FlowDistribution", FlowResponse.Distribution.class),
                  Map.entry("FlowAgingTask", FlowResponse.AgingTask.class),
              Map.entry("FlowColumn", FlowResponse.Column.class),
                  Map.entry("FlowControlChart", FlowResponse.ControlChart.class),
              Map.entry("FlowControlPoint", FlowResponse.ControlPoint.class),
                  Map.entry("FlowLittlesLaw", FlowResponse.LittlesLawCheck.class));
      records.forEach(
          (name, type) ->
              assertThat(((Map<String, Object>) schemas.get(name).get("properties")).keySet())
                  .as(name)
                  .containsExactlyInAnyOrderElementsOf(
                      Arrays.stream(type.getRecordComponents())
                          .map(java.lang.reflect.RecordComponent::getName)
                          .toList()));
      var signals =
          (Map<String, Object>)
              ((Map<String, Object>) schemas.get("FlowControlPoint").get("properties"))
                  .get("signals");
      assertThat((java.util.List<String>) ((Map<?, ?>) signals.get("items")).get("enum"))
          .containsExactlyInAnyOrderElementsOf(
              Arrays.stream(XmrChart.Signal.values()).map(Enum::name).toList());
      var paths = (Map<String, Map<String, Object>>) spec.get("paths");
      assertThat(paths)
          .containsKeys("/api/projects/{projectId}/flow", "/api/projects/{projectId}/wip-limits");
      var put = (Map<String, Object>) paths.get("/api/projects/{projectId}/wip-limits").get("put");
      assertThat(put.get("parameters").toString()).contains("#/components/parameters/ifMatch");
      assertThat((Map<String, Object>) put.get("responses"))
          .containsKeys("403", "409", "412", "428");
    }
  }
}
