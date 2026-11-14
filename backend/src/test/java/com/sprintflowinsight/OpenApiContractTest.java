package com.sprintflowinsight;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiContractTest {
    @Test
    @SuppressWarnings("unchecked")
    void documentsTheSupportedRestSurfaceAsValidOpenApiYaml() throws Exception {
        Path specification = Path.of("..", "docs", "openapi.yaml");
        try (InputStream input = Files.newInputStream(specification)) {
            Map<String, Object> document = new Yaml().load(input);
            assertThat(document).containsEntry("openapi", "3.0.3");

            Map<String, Object> paths = (Map<String, Object>) document.get("paths");
            assertThat(paths.keySet()).contains(
                    "/api/auth/password/reset/request",
                    "/api/auth/password/reset/recovery",
                    "/api/auth/password/reset/confirm",
                    "/api/auth/google",
                    "/api/projects/{id}/members/{email}",
                    "/api/projects/{projectId}/activity/{activityId}",
                    "/api/tasks/{taskId}/attachments/{id}/file",
                    "/api/projects/{projectId}/export.csv",
                    "/api/projects/{projectId}/export.pdf",
                    "/api/auth/preferences", "/api/workspace/trash/{kind}/{id}",
                    "/api/projects/templates", "/api/projects/{projectId}/retrospective",
                    "/api/shared-reports/{token}");

            Map<String, Object> metricsPath = (Map<String, Object>) paths.get("/api/projects/{projectId}/metrics");
            Map<String, Object> metricsGet = (Map<String, Object>) metricsPath.get("get");
            Map<String, Object> responses = (Map<String, Object>) metricsGet.get("responses");
            Map<String, Object> response = (Map<String, Object>) responses.get("200");
            Map<String, Object> content = (Map<String, Object>) response.get("content");
            Map<String, Object> json = (Map<String, Object>) content.get("application/json");
            Map<String, Object> schema = (Map<String, Object>) json.get("schema");
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            assertThat(properties).containsKeys("cycleTimeHours", "leadTimeHours", "throughput",
                    "throughputLast14Days", "velocityStoryPoints", "burndownAvailable", "burndown", "completedSprints");
            Map<String, Object> completedSprints = (Map<String, Object>) properties.get("completedSprints");
            assertThat(completedSprints).containsEntry("type", "array");
            Map<String, Object> historicalItem = (Map<String, Object>) completedSprints.get("items");
            Map<String, Object> historicalFields = (Map<String, Object>) historicalItem.get("properties");
            assertThat(historicalFields).containsKeys("id", "name", "completedAt", "plannedStoryPoints",
                    "actualStoryPoints", "completedTasks");
        }
    }
}
