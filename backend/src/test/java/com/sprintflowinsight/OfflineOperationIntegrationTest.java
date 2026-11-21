package com.sprintflowinsight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.support.ApiIntegrationTest;
import com.sprintflowinsight.workspace.project.Project;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class OfflineOperationIntegrationTest extends ApiIntegrationTest {
  @Autowired private ObjectMapper json;

  @Test
  void replaysTheThreeOperationsAndReturnsTheOriginalReceiptForRetries() throws Exception {
    var owner = user();
    var project = project(owner, null);
    var task = createTask(owner, project);
    long version = task.get("version").asLong();
    long taskId = task.get("id").asLong();
    var move = Map.of("kind", "MOVE", "status", "In Progress");
    var moveId = UUID.randomUUID();
    long afterMove =
        read(replay(owner, project, taskId, moveId, version, move, owner.getId())
                .andExpect(status().isOk()))
            .get("taskVersion")
            .asLong();
    assertThat(afterMove).isGreaterThan(version);
    replay(owner, project, taskId, moveId, version, move, owner.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taskVersion").value((int) afterMove));
    replay(
            owner,
            project,
            taskId,
            moveId,
            version,
            Map.of("kind", "MOVE", "status", "Review"),
            owner.getId())
        .andExpect(status().isConflict());
    replay(owner, project, taskId, UUID.randomUUID(), version, move, owner.getId())
        .andExpect(status().isPreconditionFailed());

    var comment = Map.of("kind", "COMMENT", "message", "A saved offline comment");
    var log =
        Map.of(
            "kind",
            "LOG_TIME",
            "time",
            Map.of("minutes", 35, "spentOn", LocalDate.now().toString()));
    for (var content : java.util.List.of(comment, log)) {
      UUID operationId = UUID.randomUUID();
      for (int attempt = 0; attempt < 2; attempt++)
        replay(owner, project, taskId, operationId, afterMove, content, owner.getId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskVersion").value((int) afterMove));
    }
    mvc.perform(get("/api/tasks/{id}", taskId).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("In Progress"));
    mvc.perform(get("/api/tasks/{id}/comments", taskId).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].message").value("A saved offline comment"));
    mvc.perform(get("/api/projects/{id}/time-entries", project.getId()).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].minutes").value(35));
  }

  @Test
  void checksAccountProjectRoleAndArchivingBeforeReplaying() throws Exception {
    var owner = user();
    var member = user();
    var admin = user();
    var viewer = user();
    var stranger = user();
    var project = project(owner, member);
    member(project, admin, "Admin");
    member(project, viewer, "Viewer");
    var task = createTask(owner, project);
    long version = task.get("version").asLong();
    long taskId = task.get("id").asLong();
    var comment = Map.of("kind", "COMMENT", "message", "Permitted role only");

    for (var account : java.util.List.of(admin, member))
      replay(account, project, taskId, UUID.randomUUID(), version, comment, account.getId())
          .andExpect(status().isOk());
    for (var account : java.util.List.of(viewer, stranger))
      replay(account, project, taskId, UUID.randomUUID(), version, comment, account.getId())
          .andExpect(status().isForbidden());
    replay(admin, project, taskId, UUID.randomUUID(), version, comment, member.getId())
        .andExpect(status().isForbidden());
    replay(owner, project, taskId, UUID.randomUUID(), null, comment, owner.getId())
        .andExpect(status().isPreconditionRequired());
    replay(
            owner,
            project,
            taskId,
            UUID.randomUUID(),
            version,
            Map.of("kind", "COMMENT", "status", "Done", "message", "Ambiguous"),
            owner.getId())
        .andExpect(status().isBadRequest());
    mvc.perform(
            put("/api/projects/{id}/archive", project.getId())
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"archived\":true}"))
        .andExpect(status().isOk());
    replay(member, project, taskId, UUID.randomUUID(), version, comment, member.getId())
        .andExpect(status().isConflict());
  }

  private JsonNode createTask(User owner, Project project) throws Exception {
    return read(
        mvc.perform(
                post("/api/tasks")
                    .with(as(owner))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "title",
                                "Offline task",
                                "projectId",
                                project.getId(),
                                "status",
                                "To Do"))))
            .andExpect(status().isOk()));
  }

  private ResultActions replay(
      User caller,
      Project project,
      long taskId,
      UUID id,
      Long version,
      Map<String, ?> payload,
      long accountId)
      throws Exception {
    var body = new java.util.HashMap<String, Object>(payload);
    body.put("accountId", accountId);
    body.put("operationId", id);
    body.put("taskId", taskId);
    var request =
        post("/api/projects/{id}/offline-operations", project.getId())
            .with(as(caller))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body));
    if (version != null) request.header("If-Match", "\"" + version + "\"");
    return mvc.perform(request);
  }

  private JsonNode read(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString());
  }
}
