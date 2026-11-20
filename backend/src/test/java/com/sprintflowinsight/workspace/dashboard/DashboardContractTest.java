package com.sprintflowinsight.workspace.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class DashboardContractTest {
  @Test
  @SuppressWarnings("unchecked")
  void documentsExactRecordsWidgetTypesAndConcurrency() throws Exception {
    try (var input = Files.newInputStream(Path.of("..", "docs", "openapi.yaml"))) {
      Map<String, Object> doc = new Yaml().load(input);
      var schemas =
          (Map<String, Map<String, Object>>) ((Map<?, ?>) doc.get("components")).get("schemas");
      Map.of(
              "DashboardLayout",
              DashboardResponse.class,
              "DashboardLayoutRequest",
              DashboardRequest.class,
              "DashboardWidget",
              DashboardWidget.class)
          .forEach(
              (name, type) ->
                  assertThat(((Map<String, Object>) schemas.get(name).get("properties")).keySet())
                      .containsExactlyInAnyOrderElementsOf(
                          Arrays.stream(type.getRecordComponents())
                              .map(java.lang.reflect.RecordComponent::getName)
                              .toList()));
      var properties =
          (Map<String, Map<String, Object>>) schemas.get("DashboardWidget").get("properties");
      assertThat((java.util.List<String>) properties.get("type").get("enum"))
          .containsExactlyElementsOf(Arrays.stream(WidgetType.values()).map(Enum::name).toList());
      var paths = (Map<String, Map<String, Object>>) doc.get("paths");
      var path = paths.get("/api/projects/{projectId}/dashboard");
      assertThat(path).containsKeys("get", "put");
      var write = (Map<String, Object>) path.get("put");
      assertThat(write.get("parameters").toString()).contains("ifMatch");
      assertThat((Map<String, Object>) write.get("responses"))
          .containsKeys("403", "404", "409", "412", "428");
    }
  }
}
