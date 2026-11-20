package com.sprintflowinsight.planning.sprint;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class SprintPlanningContractTest {
  @Test
  @SuppressWarnings("unchecked")
  void documentsTheScenarioAndResultRecords() throws Exception {
    try (var input = Files.newInputStream(Path.of("..", "docs", "openapi.yaml"))) {
      Map<String, Object> spec = new Yaml().load(input);
      Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
      assertThat(paths)
          .containsKeys(
              "/api/projects/{projectId}/sprint-plan/options",
              "/api/projects/{projectId}/sprint-plan/optimize");
      Map<String, Object> schemas =
          (Map<String, Object>) ((Map<String, Object>) spec.get("components")).get("schemas");
      Map.of(
              "SprintPlanRequest", SprintPlanRequest.class,
              "SprintPlanningOptions", SprintPlanningViews.Options.class,
              "SprintPlanningProposal", SprintPlanningViews.Proposal.class)
          .forEach(
              (name, type) -> {
                Map<String, Object> properties =
                    (Map<String, Object>)
                        ((Map<String, Object>) schemas.get(name)).get("properties");
                assertThat(properties.keySet())
                    .as(name)
                    .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(type.getRecordComponents())
                            .map(RecordComponent::getName)
                            .toList());
              });
    }
  }
}
