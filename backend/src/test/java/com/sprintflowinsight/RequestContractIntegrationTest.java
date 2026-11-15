package com.sprintflowinsight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.support.ApiIntegrationTest;
import com.sprintflowinsight.workspace.project.Project;
import com.sprintflowinsight.workspace.task.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Request bodies are explicit records: clients can no longer mass-assign server-owned fields, and
 * oversize or malformed input is a 400 with a field list instead of a database error.
 */
class RequestContractIntegrationTest extends ApiIntegrationTest {
  @Autowired private ObjectMapper json;
  @Autowired private TaskRepository tasks;

  @Test
  void serverOwnedTaskFieldsCannotBeMassAssigned() throws Exception {
    User owner = user();
    Project project = project(owner, null);
    String body =
        """
        {"title":"Write tests","status":"To Do","projectId":%d,"storyPoints":2,
         "id":424242,"version":99,"createdAt":"2000-01-01T00:00:00Z",
         "completedAt":"2000-01-02T00:00:00Z","trashedAt":"2000-01-03T00:00:00Z"}
        """
            .formatted(project.getId());
    JsonNode created =
        read(mvc.perform(json(post("/api/tasks"), owner, body)).andExpect(status().isOk()));

    assertThat(created.get("id").asLong()).isNotEqualTo(424242);
    assertThat(created.get("version").asLong()).isZero();
    var stored = tasks.findById(created.get("id").asLong()).orElseThrow();
    assertThat(stored.getTrashedAt()).isNull();
    assertThat(stored.getCompletedAt()).isNull();
    assertThat(stored.getCreatedAt()).isAfter(java.time.Instant.parse("2020-01-01T00:00:00Z"));
  }

  @Test
  void projectOwnershipComesFromTheSessionNotTheBody() throws Exception {
    User owner = user();
    User victim = user();
    String body =
        """
        {"name":"Owned %s","description":"x","ownerEmail":"%s","archivedAt":"2000-01-01T00:00:00Z"}
        """
            .formatted(java.util.UUID.randomUUID(), victim.getEmail());
    JsonNode created =
        read(mvc.perform(json(post("/api/projects"), owner, body)).andExpect(status().isOk()));
    assertThat(created.get("ownerEmail").asText()).isEqualTo(owner.getEmail());
    assertThat(created.get("archivedAt").isNull()).isTrue();
  }

  @Test
  void oversizeTextIsAValidationErrorNotADatabaseConflict() throws Exception {
    User owner = user();
    Project project = project(owner, null);
    String body =
        """
        {"title":"%s","status":"To Do","projectId":%d}
        """
            .formatted("x".repeat(256), project.getId());
    mvc.perform(json(post("/api/tasks"), owner, body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("title"));
  }

  @Test
  void preferencesAreTypedAndSavedFiltersAreValidated() throws Exception {
    User owner = user();
    mvc.perform(json(put("/api/auth/preferences"), owner, "{\"theme\":\"neon\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Theme must be light or dark."));
    mvc.perform(json(put("/api/auth/preferences"), owner, "{\"notifications\":{\"on\":true}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    mvc.perform(
            json(
                put("/api/auth/preferences"),
                owner,
                "{\"savedFilters\":\"[{\\\"id\\\":\\\"a\\\",\\\"name\\\":\\\"\\\"}]\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    String filters =
        "[{\\\"id\\\":\\\"a\\\",\\\"name\\\":\\\"Mine\\\",\\\"assignee\\\":\\\"me\\\"}]";
    mvc.perform(
            json(
                put("/api/auth/preferences"),
                owner,
                "{\"language\":\"SV\",\"savedFilters\":\"" + filters + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.language").value("SV"))
        .andExpect(jsonPath("$.theme").value("light"))
        .andExpect(jsonPath("$.savedFilters").value(filters.replace("\\\"", "\"")));
  }

  @Test
  void archiveAndLinkBodiesAreRequiredAndValidated() throws Exception {
    User owner = user();
    Project project = project(owner, null);
    mvc.perform(json(put("/api/projects/" + project.getId() + "/archive"), owner, "{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("archived"));
    mvc.perform(json(put("/api/projects/" + project.getId() + "/owner"), owner, "{\"email\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  private MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder request, User caller, String body) {
    return request
        .with(as(caller))
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private JsonNode read(org.springframework.test.web.servlet.ResultActions result)
      throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString());
  }
}
