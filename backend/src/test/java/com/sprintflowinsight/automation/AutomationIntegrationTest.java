package com.sprintflowinsight.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.support.ApiIntegrationTest;
import com.sprintflowinsight.workspace.project.Project;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AutomationIntegrationTest extends ApiIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired OverdueAutomationJob overdue;

  @Test
  void restrictsRuleManagementAndProtectsAnExistingRevision() throws Exception {
    var owner = user();
    var project = project(owner, null);
    String endpoint = endpoint(project) + "/rules";
    for (String role : List.of("Owner", "Admin", "Member", "Viewer", "Outsider")) {
      var caller = role.equals("Owner") ? owner : user();
      if (List.of("Admin", "Member", "Viewer").contains(role)) member(project, caller, role);
      boolean canManage = List.of("Owner", "Admin").contains(role);
      mvc.perform(get(endpoint).with(as(caller))).andExpect(status().is(canManage ? 200 : 403));
      mvc.perform(
              post(endpoint)
                  .with(as(caller))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(ruleBody(role, "when overdue\nthen label \"late\"")))
          .andExpect(status().is(canManage ? 201 : 403));
    }
    long id =
        createRule(project, owner, "Versioned", "when overdue\nthen notify \"A task is late\"");
    String item = endpoint + "/" + id;
    var draft = ruleBody("Revised", "when overdue\nthen label \"late\"");
    mvc.perform(
            put(item)
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(draft))
        .andExpect(status().isPreconditionRequired());
    mvc.perform(
            put(item)
                .with(as(owner))
                .with(csrf())
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(draft))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(delete(item).with(as(owner)).with(csrf()).header("If-Match", "\"0\""))
        .andExpect(status().isPreconditionFailed());
    jdbc.update("update projects set archived_at=now() where id=?", project.getId());
    mvc.perform(get(endpoint).with(as(owner))).andExpect(status().isOk());
    mvc.perform(delete(item).with(as(owner)).with(csrf()).header("If-Match", "\"1\""))
        .andExpect(status().isConflict());
  }

  @Test
  void movesThroughTwoRulesWithoutReenteringAnAncestorRule() throws Exception {
    var owner = user();
    var project = project(owner, null);
    long first =
        createRule(
            project,
            owner,
            "Review return",
            "when task-moved to \"Review\"\nthen move \"In Progress\"");
    createRule(
        project, owner, "Work return", "when task-moved to \"In Progress\"\nthen move \"Review\"");
    long task = createTask(project, owner).path("id").asLong();
    mvc.perform(
            put("/api/tasks/" + task)
                .with(as(owner))
                .with(csrf())
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "projectId",
                            project.getId(),
                            "title",
                            "Rule task",
                            "status",
                            "Review",
                            "storyPoints",
                            3))))
        .andExpect(status().isOk());
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(
                        jdbc.queryForList(
                            "select outcome from automation_run where project_id=? order by id",
                            String.class,
                            project.getId()))
                    .containsExactly("APPLIED", "APPLIED", "SKIPPED"));
    assertThat(
            jdbc.queryForObject(
                "select rule_id from automation_run where project_id=? and outcome='SKIPPED'",
                Long.class,
                project.getId()))
        .isEqualTo(first);
    assertThat(jdbc.queryForObject("select status from task where id=?", String.class, task))
        .isEqualTo("Review");
    assertThat(AutomationWorker.nextTrail(List.of(1L, 2L, 3L), 4L)).isEmpty();
  }

  @Test
  void handlesOverdueCustomFieldConditionsAndDoesNotDuplicateActions() throws Exception {
    var owner = user();
    var teammate = user();
    var project = project(owner, teammate);
    long id = createTask(project, owner).path("id").asLong();
    long field =
        jdbc.queryForObject(
            "insert into custom_field(project_id,name,kind) values(?,'Risk','NUMBER') returning id",
            Long.class,
            project.getId());
    jdbc.update(
        "insert into task_custom_value(task_id,field_id,project_id,kind,number_value)"
            + " values(?,?,?,'NUMBER',8)",
        id,
        field,
        project.getId());
    jdbc.update("update task set due_at=now()-interval '1 day' where id=?", id);
    createRule(
        project,
        owner,
        "Comment on risk",
        "when overdue\nif field "
            + field
            + " greater-than \"5\"\nthen comment \"Investigate this risk.\"");
    createRule(
        project,
        owner,
        "Assign overdue",
        "when overdue\nthen assign \"" + teammate.getEmail() + "\"");
    createRule(
        project, owner, "Notify overdue", "when overdue\nthen notify \"Risk action applied.\"");
    overdue.runAt(Instant.now());
    overdue.runAt(Instant.now());
    assertThat(
            jdbc.queryForList("select message from task_comment where task_id=?", String.class, id))
        .containsExactly("Investigate this risk.");
    assertThat(jdbc.queryForObject("select assignee_email from task where id=?", String.class, id))
        .isEqualTo(teammate.getEmail());
    assertThat(
            jdbc.queryForList(
                "select outcome from automation_run where task_id=?", String.class, id))
        .containsExactly("APPLIED", "APPLIED", "APPLIED");
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(
                        jdbc.queryForList(
                            "select message from notification where project_id=?",
                            String.class,
                            project.getId()))
                    .contains("Risk action applied."));
  }

  @Test
  void signsOnlyTheConfiguredRepositoryAndDeduplicatesAMergedPullRequest() throws Exception {
    var owner = user();
    var project = project(owner, null);
    var task = createTask(project, owner);
    long taskId = task.path("id").asLong();
    createRule(project, owner, "PR label", "when pull-request-merged\nthen label \"merged\"");
    var settings =
        mvc.perform(
                put(endpoint(project) + "/github")
                    .with(as(owner))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"repository\":\"owner/repo\"}"))
            .andExpect(status().isOk())
            .andReturn();
    var issued = json.readTree(settings.getResponse().getContentAsString());
    String secret = issued.path("secret").asText();
    String hook = "/api/automation/github/" + issued.path("settings").path("endpointId").asText();
    mvc.perform(get(endpoint(project) + "/github").with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.secret").doesNotExist());
    String payload =
        """
        {"action":"closed","repository":{"full_name":"owner/repo"},"pull_request":{
           "id":9001,"merged":true,"title":"Finish %s-1"}}
        """
            .formatted(project.getKey());
    mvc.perform(
            post(hook)
                .header("X-GitHub-Event", "pull_request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isUnauthorized());
    String incorrect = payload.replace("owner/repo", "other/repo");
    mvc.perform(
            post(hook)
                .header("X-GitHub-Event", "pull_request")
                .header("X-Hub-Signature-256", sign(secret, incorrect))
                .contentType(MediaType.APPLICATION_JSON)
                .content(incorrect))
        .andExpect(status().isUnauthorized());
    for (int i = 0; i < 2; i++) {
      mvc.perform(
              post(hook)
                  .header("X-GitHub-Event", "pull_request")
                  .header("X-Hub-Signature-256", sign(secret, payload))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(payload))
          .andExpect(status().isNoContent());
    }
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(
                        jdbc.queryForList(
                            "select outcome from automation_run where task_id=?",
                            String.class,
                            taskId))
                    .containsExactly("APPLIED"));
    assertThat(jdbc.queryForObject("select labels from task where id=?", String.class, taskId))
        .contains("merged");
  }

  private long createRule(Project p, User actor, String name, String source) throws Exception {
    return json.readTree(
            mvc.perform(
                    post(endpoint(p) + "/rules")
                        .with(as(actor))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody(name, source)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .path("id")
        .asLong();
  }

  private String ruleBody(String name, String source) throws Exception {
    return json.writeValueAsString(Map.of("name", name, "source", source, "enabled", true));
  }

  private JsonNode createTask(Project p, User actor) throws Exception {
    return json.readTree(
        mvc.perform(
                post("/api/tasks")
                    .with(as(actor))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "projectId",
                                p.getId(),
                                "title",
                                "Rule task",
                                "status",
                                "To Do",
                                "storyPoints",
                                3))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private static String endpoint(Project project) {
    return "/api/projects/" + project.getId() + "/automation";
  }

  private static String sign(String secret, String body) throws Exception {
    var mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
  }
}
