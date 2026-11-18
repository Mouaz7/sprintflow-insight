package com.sprintflowinsight.workspace.roadmap;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class RoadmapContractTest {
  @Test
  @SuppressWarnings("unchecked")
  void documentsEveryRoadmapRecordAndPrecondition() throws Exception {
    try (var input = Files.newInputStream(Path.of("..", "docs", "openapi.yaml"))) {
      Map<String, Object> specification = new Yaml().load(input);
      var schemas =
          (Map<String, Map<String, Object>>)
              ((Map<?, ?>) specification.get("components")).get("schemas");
      var requests =
          new Class<?>[] {
            EpicRequest.class,
            StoryEpicRequest.class,
            DependencyRequest.class,
            EstimateRequest.class
          };
      var records = new java.util.ArrayList<Class<?>>(Arrays.asList(requests));
      Arrays.stream(RoadmapViews.class.getDeclaredClasses())
          .filter(Class::isRecord)
          .forEach(records::add);
      for (var record : records) {
        assertThat(schemas).containsKey(record.getSimpleName());
        var fields = (Map<String, Object>) schemas.get(record.getSimpleName()).get("properties");
        assertThat(fields.keySet())
            .as(record.getSimpleName())
            .containsExactlyInAnyOrderElementsOf(
                Arrays.stream(record.getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getName)
                    .toList());
      }
      var paths = (Map<String, Map<String, Object>>) specification.get("paths");
      for (String suffix :
          new String[] {
            "epics",
            "epics/{epicId}",
            "stories/{storyId}/epic",
            "roadmap",
            "dependencies",
            "dependencies/{dependencyId}",
            "estimates",
            "tasks/{taskId}/estimate"
          }) {
        assertThat(paths).containsKey("/api/projects/{projectId}/" + suffix);
      }
      for (String suffix : new String[] {"epics/{epicId}", "stories/{storyId}/epic"}) {
        var put = (Map<String, Object>) paths.get("/api/projects/{projectId}/" + suffix).get("put");
        assertThat(put.get("parameters").toString()).contains("#/components/parameters/ifMatch");
        assertThat((Map<String, Object>) put.get("responses")).containsKeys("412", "428");
      }
      assertThat(schemas.get("Forecast").get("description").toString())
          .contains("outside ancestors", "unplanned stories", "optimistic");
    }
  }
}
