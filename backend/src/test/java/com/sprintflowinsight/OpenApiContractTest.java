package com.sprintflowinsight;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class OpenApiContractTest {
  @Test
  @SuppressWarnings("unchecked")
  void documentsTheSupportedRestSurfaceAsValidOpenApiYaml() throws Exception {
    Path specification = Path.of("..", "docs", "openapi.yaml");
    try (InputStream input = Files.newInputStream(specification)) {
      Map<String, Object> document = new Yaml().load(input);
      assertThat(document).containsEntry("openapi", "3.0.3");

      Map<String, Object> paths = (Map<String, Object>) document.get("paths");
      assertThat(paths.keySet())
          .contains(
              "/api/auth/password/reset/request",
              "/api/auth/password/reset/recovery",
              "/api/auth/password/reset/confirm",
              "/api/auth/google",
              "/api/projects/{id}/members/{email}",
              "/api/projects/{projectId}/activity/{activityId}",
              "/api/tasks/{taskId}/attachments/{id}/file",
              "/api/projects/{projectId}/export.csv",
              "/api/projects/{projectId}/export.pdf",
              "/api/auth/preferences",
              "/api/workspace/trash/{kind}/{id}",
              "/api/projects/templates",
              "/api/projects/{projectId}/retrospective",
              "/api/shared-reports/{token}");

      Map<String, Object> metricsPath =
          (Map<String, Object>) paths.get("/api/projects/{projectId}/metrics");
      Map<String, Object> metricsGet = (Map<String, Object>) metricsPath.get("get");
      Map<String, Object> responses = (Map<String, Object>) metricsGet.get("responses");
      Map<String, Object> response = (Map<String, Object>) responses.get("200");
      Map<String, Object> content = (Map<String, Object>) response.get("content");
      Map<String, Object> json = (Map<String, Object>) content.get("application/json");
      Map<String, Object> schema = (Map<String, Object>) json.get("schema");
      Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
      assertThat(properties)
          .containsKeys(
              "cycleTimeHours",
              "leadTimeHours",
              "throughput",
              "throughputLast14Days",
              "velocityStoryPoints",
              "burndownAvailable",
              "burndown",
              "completedSprints");
      Map<String, Object> completedSprints =
          (Map<String, Object>) properties.get("completedSprints");
      assertThat(completedSprints).containsEntry("type", "array");
      Map<String, Object> historicalItem = (Map<String, Object>) completedSprints.get("items");
      Map<String, Object> historicalFields = (Map<String, Object>) historicalItem.get("properties");
      assertThat(historicalFields)
          .containsKeys(
              "id",
              "name",
              "completedAt",
              "plannedStoryPoints",
              "actualStoryPoints",
              "completedTasks");

      Map<String, Object> schemas =
          (Map<String, Object>) ((Map<String, Object>) document.get("components")).get("schemas");
      Map<String, Object> problem = (Map<String, Object>) schemas.get("Problem");
      Map<String, Object> code =
          (Map<String, Object>) ((Map<String, Object>) problem.get("properties")).get("code");
      assertThat((java.util.List<String>) code.get("enum"))
          .as("every ErrorCode is documented, and nothing else")
          .containsExactlyInAnyOrderElementsOf(
              java.util.Arrays.stream(com.sprintflowinsight.common.error.ErrorCode.values())
                  .map(Enum::name)
                  .toList());

      // Documented request schemas must list exactly the fields the request records accept.
      Map<String, Class<? extends Record>> requests =
          Map.of(
              "ProjectRequest", com.sprintflowinsight.workspace.project.ProjectRequest.class,
              "StoryRequest", com.sprintflowinsight.workspace.story.StoryRequest.class,
              "SprintRequest", com.sprintflowinsight.workspace.sprint.SprintRequest.class,
              "TaskRequest", com.sprintflowinsight.workspace.task.TaskRequest.class,
              "PreferencesRequest", com.sprintflowinsight.auth.PreferencesRequest.class);
      requests.forEach(
          (name, type) -> {
            Map<String, Object> documented = (Map<String, Object>) schemas.get(name);
            assertThat(documented).as(name + " is documented").isNotNull();
            assertThat(((Map<String, Object>) documented.get("properties")).keySet())
                .as(name + " properties")
                .containsExactlyInAnyOrderElementsOf(
                    java.util.Arrays.stream(type.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toList());
          });
    }
  }
}
