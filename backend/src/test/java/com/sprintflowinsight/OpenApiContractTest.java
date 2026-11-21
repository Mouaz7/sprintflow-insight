package com.sprintflowinsight;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.workspace.project.ProjectKeys;
import com.sprintflowinsight.workspace.project.ProjectRequest;
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
              "/api/auth/two-factor",
              "/api/auth/two-factor/setup",
              "/api/auth/two-factor/enable",
              "/api/auth/two-factor/disable",
              "/api/auth/two-factor/verify",
              "/api/organizations/{slug}/security",
              "/api/projects/{id}/members/{email}",
              "/api/projects/{projectId}/activity/{activityId}",
              "/api/tasks/{taskId}/attachments/{id}/file",
              "/api/projects/{projectId}/export.csv",
              "/api/projects/{projectId}/export.pdf",
              "/api/auth/preferences",
              "/api/workspace/trash/{kind}/{id}",
              "/api/projects/templates",
              "/api/organizations",
              "/api/organizations/{slug}/projects",
              "/api/organizations/{slug}/members",
              "/api/organizations/{slug}/members/{id}",
              "/api/organizations/{slug}/teams",
              "/api/organizations/{slug}/teams/{id}/members/{userId}",
              "/api/organizations/{slug}/invites/accept",
              "/api/organizations/{slug}/templates/{id}/projects",
              "/api/projects/{projectId}/retrospective",
              "/api/projects/{projectId}/collaboration/{target}/{id}",
              "/api/projects/{projectId}/collaboration/{target}/{id}/metadata",
              "/api/projects/{projectId}/collaboration/{target}/{id}/projection",
              "/api/shared-reports/{token}",
              "/api/projects/{projectId}/events");

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
      Map<String, Object> realtime =
          (Map<String, Object>)
              ((Map<String, Object>) schemas.get("RealtimeMessage")).get("properties");
      assertThat(realtime).containsOnlyKeys("projectId", "seq", "type", "entityId", "version");
      assertThat(
              (Map<String, Object>)
                  ((Map<String, Object>) schemas.get("OpenTaskPresenceRequest")).get("properties"))
          .containsOnlyKeys(
              java.util.Arrays.stream(
                      com.sprintflowinsight.realtime.OpenTaskRequest.class.getRecordComponents())
                  .map(java.lang.reflect.RecordComponent::getName)
                  .toArray(String[]::new));
      assertThat(
              (Map<String, Object>)
                  ((Map<String, Object>) schemas.get("PresenceMessage")).get("properties"))
          .containsOnlyKeys("projectId", "viewers");
      assertThat(
              (Map<String, Object>)
                  ((Map<String, Object>) schemas.get("RealtimeEventPage")).get("properties"))
          .containsOnlyKeys("projectId", "latestSeq", "resync", "events");
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

      // B16: records and typed conditions stay aligned with the public REST contract.
      assertThat(paths)
          .containsKeys(
              "/api/projects/{projectId}/custom-fields",
              "/api/projects/{projectId}/custom-fields/{fieldId}",
              "/api/projects/{projectId}/custom-fields/matches",
              "/api/tasks/{taskId}/custom-fields");
      Map<String, Class<? extends Record>> customRecords =
          Map.of(
              "CustomFieldDefinitionRequest",
                  com.sprintflowinsight.workspace.customfield.FieldDefinitionRequest.class,
              "CustomFieldValuesRequest",
                  com.sprintflowinsight.workspace.customfield.FieldValuesRequest.class,
              "CustomFieldValueEntry",
                  com.sprintflowinsight.workspace.customfield.FieldValuesRequest.Entry.class,
              "CustomFieldDefinition",
                  com.sprintflowinsight.workspace.customfield.CustomFieldViews.Definition.class,
              "CustomFieldValue",
                  com.sprintflowinsight.workspace.customfield.CustomFieldViews.Value.class,
              "TaskCustomFields",
                  com.sprintflowinsight.workspace.customfield.CustomFieldViews.TaskValues.class,
              "CustomFieldMatches",
                  com.sprintflowinsight.workspace.customfield.CustomFieldViews.Matches.class);
      customRecords.forEach(
          (name, type) ->
              assertThat(
                      ((Map<String, Object>)
                              ((Map<String, Object>) schemas.get(name)).get("properties"))
                          .keySet())
                  .as(name)
                  .containsExactlyInAnyOrderElementsOf(
                      java.util.Arrays.stream(type.getRecordComponents())
                          .map(java.lang.reflect.RecordComponent::getName)
                          .toList()));
      assertThat(
              (java.util.List<String>)
                  ((Map<String, Object>) schemas.get("CustomFieldKind")).get("enum"))
          .containsExactlyElementsOf(
              java.util.Arrays.stream(
                      com.sprintflowinsight.workspace.customfield.FieldKind.values())
                  .map(Enum::name)
                  .toList());
      assertThat(
              (java.util.List<String>)
                  ((Map<String, Object>) schemas.get("CustomFieldOperator")).get("enum"))
          .containsExactlyElementsOf(
              java.util.Arrays.stream(
                      com.sprintflowinsight.workspace.customfield.FieldOperator.values())
                  .map(Enum::name)
                  .toList());

      assertThat(paths.keySet())
          .containsAll(
              java.util.List.of(
                  "/api/projects/{projectId}/imports/preview",
                  "/api/projects/{projectId}/imports",
                  "/api/projects/{projectId}/audit",
                  "/api/account/export"));
      Map<String, Class<? extends Record>> transferRecords =
          Map.of(
              "ImportRequest",
              com.sprintflowinsight.workspace.transfer.ImportRequest.class,
              "ImportPreview",
              com.sprintflowinsight.workspace.transfer.ImportViews.Preview.class,
              "ImportRow",
              com.sprintflowinsight.workspace.transfer.ImportViews.Row.class,
              "ImportResult",
              com.sprintflowinsight.workspace.transfer.ImportViews.Result.class,
              "ImportBatch",
              com.sprintflowinsight.workspace.transfer.ImportViews.Batch.class,
              "AuditEntry",
              com.sprintflowinsight.common.audit.AuditService.Entry.class,
              "AuditPage",
              com.sprintflowinsight.common.audit.AuditService.Page.class);
      transferRecords.forEach(
          (name, type) ->
              assertThat(
                      ((Map<String, Object>)
                              ((Map<String, Object>) schemas.get(name)).get("properties"))
                          .keySet())
                  .as(name)
                  .containsExactlyInAnyOrderElementsOf(
                      java.util.Arrays.stream(type.getRecordComponents())
                          .map(java.lang.reflect.RecordComponent::getName)
                          .toList()));
      assertThat(
              (java.util.List<String>)
                  ((Map<String, Object>) schemas.get("ImportFormat")).get("enum"))
          .containsExactlyElementsOf(
              java.util.Arrays.stream(
                      com.sprintflowinsight.workspace.transfer.ImportFormat.values())
                  .map(Enum::name)
                  .toList());

      assertThat(paths).containsKey("/api/projects/{projectId}/forecast/backtest");
      Map<String, Class<? extends Record>> forecastRecords =
          Map.of(
              "ForecastBacktestResponse",
              com.sprintflowinsight.reporting.ForecastBacktestResponse.class,
              "ForecastBacktestCandidate",
              com.sprintflowinsight.forecast.BacktestReport.class);
      forecastRecords.forEach(
          (name, type) ->
              assertThat(
                      ((Map<String, Object>)
                              ((Map<String, Object>) schemas.get(name)).get("properties"))
                          .keySet())
                  .as(name)
                  .containsExactlyInAnyOrderElementsOf(
                      java.util.Arrays.stream(type.getRecordComponents())
                          .map(java.lang.reflect.RecordComponent::getName)
                          .toList()));

      // B0: task identifiers and project keys are part of the response contract.
      assertThat(paths).containsKey("/api/tasks/by-key/{key}");
      Map<String, Object> byKey =
          (Map<String, Object>)
              ((Map<String, Object>) paths.get("/api/tasks/by-key/{key}")).get("get");
      assertThat(((Map<String, Object>) byKey.get("responses")).keySet())
          .contains("200", "401", "404");
      Map<String, Object> task =
          (Map<String, Object>) ((Map<String, Object>) schemas.get("Task")).get("properties");
      assertThat(task).containsKeys("number", "projectKey", "key");
      Map<String, Object> project =
          (Map<String, Object>) ((Map<String, Object>) schemas.get("Project")).get("properties");
      Map<String, Object> projectKey = (Map<String, Object>) project.get("key");
      assertThat(projectKey).containsEntry("pattern", "^" + ProjectKeys.REGEX + "$");
      Map<String, Object> requestKey =
          (Map<String, Object>)
              ((Map<String, Object>)
                      ((Map<String, Object>) schemas.get("ProjectRequest")).get("properties"))
                  .get("key");
      assertThat(requestKey).containsEntry("pattern", "^" + ProjectRequest.KEY_INPUT + "$");

      // B9: search parameters and result shapes follow the request and response records.
      Map<String, Object> search =
          (Map<String, Object>) ((Map<String, Object>) paths.get("/api/search")).get("get");
      assertThat(
              ((java.util.List<Map<String, Object>>) search.get("parameters"))
                  .stream().map(parameter -> parameter.get("name")))
          .containsExactlyInAnyOrderElementsOf(
              java.util.Arrays.stream(
                      com.sprintflowinsight.search.SearchRequest.class.getRecordComponents())
                  .map(java.lang.reflect.RecordComponent::getName)
                  .toList());
      assertThat(((Map<String, Object>) search.get("responses")).keySet())
          .contains("200", "400", "401", "403", "404");
      Map<String, Class<? extends Record>> results =
          Map.of(
              "SearchResponse", com.sprintflowinsight.search.SearchResponse.class,
              "SearchHit", com.sprintflowinsight.search.SearchHit.class,
              "SearchSegment", com.sprintflowinsight.search.SearchHit.Segment.class);
      results.forEach(
          (name, type) ->
              assertThat(
                      ((Map<String, Object>)
                              ((Map<String, Object>) schemas.get(name)).get("properties"))
                          .keySet())
                  .as(name + " properties")
                  .containsExactlyInAnyOrderElementsOf(
                      java.util.Arrays.stream(type.getRecordComponents())
                          .map(java.lang.reflect.RecordComponent::getName)
                          .toList()));
      Map<String, Object> hitType =
          (Map<String, Object>)
              ((Map<String, Object>)
                      ((Map<String, Object>) schemas.get("SearchHit")).get("properties"))
                  .get("type");
      assertThat((java.util.List<String>) hitType.get("enum"))
          .containsExactlyElementsOf(
              java.util.Arrays.stream(com.sprintflowinsight.search.SearchType.values())
                  .map(com.sprintflowinsight.search.SearchType::wire)
                  .toList());
    }
  }
}
