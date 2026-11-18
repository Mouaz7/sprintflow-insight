package com.sprintflowinsight.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.forecast.MonteCarloSimulation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class ForecastContractTest {
  @Test
  @SuppressWarnings("unchecked")
  void documentsExactForecastRecordsWarningsAndReadBoundary() throws Exception {
    try (var input = Files.newInputStream(Path.of("..", "docs", "openapi.yaml"))) {
      Map<String, Object> spec = new Yaml().load(input);
      var schemas =
          (Map<String, Map<String, Object>>) ((Map<?, ?>) spec.get("components")).get("schemas");
      var records =
          Map.ofEntries(
              Map.entry("ForecastQuery", ForecastRequest.class),
              Map.entry("ForecastResponse", ForecastResponse.class),
              Map.entry("ForecastHistoryPeriod", ForecastResponse.HistoryPeriod.class),
              Map.entry("ForecastWorkScope", ForecastResponse.WorkScope.class),
              Map.entry("ForecastCompletionDate", ForecastResponse.CompletionDate.class),
              Map.entry("ForecastBin", MonteCarloSimulation.Bin.class),
              Map.entry("ForecastItemsAtConfidence", ForecastResponse.ItemsAtConfidence.class),
              Map.entry("ForecastItemsByDate", ForecastResponse.ItemsByDate.class),
              Map.entry("ForecastTarget", ForecastResponse.TargetProbability.class));
      records.forEach(
          (name, type) ->
              assertThat(((Map<String, Object>) schemas.get(name).get("properties")).keySet())
                  .as(name)
                  .containsExactlyInAnyOrderElementsOf(
                      Arrays.stream(type.getRecordComponents())
                          .map(java.lang.reflect.RecordComponent::getName)
                          .toList()));
      var properties =
          (Map<String, Map<String, Object>>) schemas.get("ForecastResponse").get("properties");
      assertThat(
              (java.util.List<String>)
                  ((Map<?, ?>) properties.get("warnings").get("items")).get("enum"))
          .containsExactlyInAnyOrderElementsOf(
              Arrays.stream(ForecastResponse.Warning.values()).map(Enum::name).toList());
      assertThat(properties.get("seed").get("type")).isEqualTo("string");
      assertThat(schemas.get("ForecastItemsByDate").get("nullable")).isEqualTo(true);
      var paths = (Map<String, Map<String, Object>>) spec.get("paths");
      var get = (Map<String, Object>) paths.get("/api/projects/{projectId}/forecast").get("get");
      assertThat((Map<String, Object>) get.get("responses"))
          .containsKeys("200", "400", "401", "403", "404");
      assertThat(paths.get("/api/projects/{projectId}/forecast"))
          .doesNotContainKeys("post", "put", "delete");
    }
  }
}
