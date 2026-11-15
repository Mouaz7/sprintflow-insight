package com.sprintflowinsight.project;

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
import com.sprintflowinsight.workspace.task.TaskRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** B0 over HTTP: project keys, task identifiers and membership by account id, per role. */
class ProjectKeyIntegrationTest extends ApiIntegrationTest {
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TaskRepository tasks;

  private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, User caller,
      String content) {
    return request.with(as(caller)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(content);
  }

  private JsonNode read(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString());
  }

  private String freshKey() {
    return uniqueKey();
  }

  private JsonNode createTask(User caller, Project project, String title) throws Exception {
    return read(
        mvc.perform(
                body(
                    post("/api/tasks"),
                    caller,
                    """
                    {"title":"%s","status":"To Do","projectId":%d,"storyPoints":1}
                    """
                        .formatted(title, project.getId())))
            .andExpect(status().isOk()));
  }

  private String update(Project project, String key) {
    return """
        {"name":"%s","description":"d","key":"%s"}
        """
        .formatted(project.getName(), key);
  }

  private String version(Project project) {
    return "\"" + projects.findById(project.getId()).orElseThrow().getVersion() + "\"";
  }

  @Test
  void createChoosesOrDerivesAUniqueKey() throws Exception {
    User owner = user();
    String key = freshKey();
    JsonNode chosen =
        read(
            mvc.perform(
                    body(
                        post("/api/projects"),
                        owner,
                        """
                        {"name":"Chosen %s","key":" %s "}
                        """
                            .formatted(UUID.randomUUID(), key.toLowerCase())))
                .andExpect(status().isOk()));
    assertThat(chosen.get("key").asString()).isEqualTo(key);

    mvc.perform(
            body(
                post("/api/projects"),
                owner,
                """
                {"name":"Again %s","key":"%s"}
                """
                    .formatted(UUID.randomUUID(), key)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("This project key is already in use."));

    for (String invalid : new String[] {"A", "S1", "ABCDEFGHIJK", "Å-Ä"})
      mvc.perform(
              body(
                  post("/api/projects"),
                  owner,
                  """
                  {"name":"Bad %s","key":"%s"}
                  """
                      .formatted(UUID.randomUUID(), invalid)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.errors[0].field").value("key"));

    JsonNode first =
        read(
            mvc.perform(
                    body(post("/api/projects"), owner, "{\"name\":\"Zebra Quest " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk()));
    JsonNode second =
        read(
            mvc.perform(
                    body(post("/api/projects"), owner, "{\"name\":\"Zebra Quest " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk()));
    assertThat(first.get("key").asString()).matches("[A-Z]{2,10}").startsWith("ZQ");
    assertThat(second.get("key").asString()).matches("[A-Z]{2,10}").startsWith("ZQ");
    assertThat(first.get("key").asString()).isNotEqualTo(second.get("key").asString());

    // The creator is an Owner member by account id, so the new project is visible at once.
    mvc.perform(get("/api/projects/" + first.get("id").asLong() + "/role").with(as(owner)))
        .andExpect(jsonPath("$.role").value("Owner"));
  }

  @Test
  void templateProjectsAcceptAKeyAndNumberTheirStarterTasks() throws Exception {
    User owner = user();
    String key = freshKey();
    JsonNode project =
        read(
            mvc.perform(
                    body(
                        post("/api/projects/templates"),
                        owner,
                        """
                        {"name":"Template %s","template":"Kanban","language":"EN","key":"%s"}
                        """
                            .formatted(UUID.randomUUID(), key)))
                .andExpect(status().isOk()));
    assertThat(project.get("key").asString()).isEqualTo(key);
    mvc.perform(get("/api/tasks").param("projectId", project.get("id").asString()).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[?(@.key == '" + key + "-1')]").isNotEmpty())
        .andExpect(jsonPath("$[?(@.key == '" + key + "-3')]").isNotEmpty());
  }

  @Test
  void tasksGetSequentialIdentifiersPerProjectThatSurviveUpdates()
      throws Exception {
    User owner = user();
    Project project = project(owner, null);
    Project other = project(owner, null);
    JsonNode first = createTask(owner, project, "First");
    JsonNode second = createTask(owner, project, "Second");
    JsonNode elsewhere = createTask(owner, other, "Elsewhere");

    assertThat(first.get("number").asInt()).isEqualTo(1);
    assertThat(first.get("projectKey").asString()).isEqualTo(project.getKey());
    assertThat(first.get("key").asString()).isEqualTo(project.getKey() + "-1");
    assertThat(second.get("key").asString()).isEqualTo(project.getKey() + "-2");
    assertThat(elsewhere.get("key").asString()).isEqualTo(other.getKey() + "-1");

    // The number is server owned: a client value is ignored on update.
    mvc.perform(
            body(
                    put("/api/tasks/" + second.get("id").asLong()),
                    owner,
                    """
                    {"title":"Second renamed","status":"Done","projectId":%d,"number":99}
                    """
                        .formatted(project.getId()))
                .header("If-Match", "\"" + second.get("version").asLong() + "\""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.key").value(project.getKey() + "-2"));

    assertThat(createTask(owner, project, "Third").get("number").asInt()).isEqualTo(3);
  }

  @Test
  void lookupByKeyFollowsReadAccessAndHidesOtherProjects() throws Exception {
    User owner = user();
    User viewer = user();
    User outsider = user();
    Project project = project(owner, null);
    member(project, viewer, "Viewer");
    JsonNode task = createTask(owner, project, "Find me");
    String key = task.get("key").asString();

    mvc.perform(get("/api/tasks/by-key/" + key).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(task.get("id").asLong()));
    mvc.perform(get("/api/tasks/by-key/" + key.toLowerCase()).with(as(viewer)))
        .andExpect(status().isOk());
    // Outsiders learn nothing: the same 404 as for a key that does not exist.
    mvc.perform(get("/api/tasks/by-key/" + key).with(as(outsider)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mvc.perform(get("/api/tasks/by-key/" + project.getKey() + "-999").with(as(owner)))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/tasks/by-key/not-a-key").with(as(owner)))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/tasks/by-key/" + key)).andExpect(status().isUnauthorized());

    Project archived = projects.findById(project.getId()).orElseThrow();
    archived.setArchivedAt(Instant.now());
    projects.save(archived);
    mvc.perform(get("/api/tasks/by-key/" + key).with(as(viewer))).andExpect(status().isOk());

    var stored = tasks.findById(task.get("id").asLong()).orElseThrow();
    stored.setTrashedAt(Instant.now());
    tasks.save(stored);
    mvc.perform(get("/api/tasks/by-key/" + key).with(as(owner))).andExpect(status().isNotFound());
  }

  @Test
  void lookupByKeyDoesNotRevealThatAProjectIsInTheRecycleBin() throws Exception {
    User owner = user();
    User outsider = user();
    Project project = project(owner, null);
    String key = createTask(owner, project, "Hidden").get("key").asString();
    Project trashed = projects.findById(project.getId()).orElseThrow();
    trashed.setTrashedAt(Instant.now());
    projects.save(trashed);

    for (User caller : new User[] {outsider, owner}) {
      mvc.perform(get("/api/tasks/by-key/" + key).with(as(caller)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("NOT_FOUND"))
          .andExpect(jsonPath("$.detail").value("Task not found."));
    }
    mvc.perform(get("/api/tasks/by-key/" + project.getKey() + "-999").with(as(outsider)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Task not found."));
  }

  @Test
  void onlyOwnersAndAdminsChangeTheKeyOfAnActiveProject() throws Exception {
    User owner = user();
    User admin = user();
    User member = user();
    User viewer = user();
    User outsider = user();
    Project project = project(owner, member);
    member(project, admin, "Admin");
    member(project, viewer, "Viewer");
    createTask(owner, project, "Renumbered");

    for (User denied : new User[] {member, viewer, outsider})
      mvc.perform(
              body(put("/api/projects/" + project.getId()), denied, update(project, freshKey()))
                  .header("If-Match", version(project)))
          .andExpect(status().isForbidden());

    String byAdmin = freshKey();
    mvc.perform(
            body(put("/api/projects/" + project.getId()), admin, update(project, byAdmin.toLowerCase()))
                .header("If-Match", version(project)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.key").value(byAdmin));
    // Existing tasks follow the project key; their numbers stay.
    mvc.perform(get("/api/tasks").param("projectId", project.getId().toString()).with(as(member)))
        .andExpect(jsonPath("$[0].key").value(byAdmin + "-1"));

    Project taken = project(owner, null);
    mvc.perform(
            body(put("/api/projects/" + project.getId()), owner, update(project, taken.getKey()))
                .header("If-Match", version(project)))
        .andExpect(status().isConflict());
    // Omitting the key keeps it.
    mvc.perform(
            body(
                    put("/api/projects/" + project.getId()),
                    owner,
                    "{\"name\":\"" + project.getName() + "\",\"description\":\"kept\"}")
                .header("If-Match", version(project)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.key").value(byAdmin));

    Project archived = projects.findById(project.getId()).orElseThrow();
    archived.setArchivedAt(Instant.now());
    projects.save(archived);
    mvc.perform(
            body(put("/api/projects/" + project.getId()), owner, update(project, freshKey()))
                .header("If-Match", version(project)))
        .andExpect(status().isConflict());
  }

  @Test
  void membershipFollowsTheAccountIdNotTheStoredEmail() throws Exception {
    User owner = user();
    User colleague = user();
    Project project = project(owner, colleague);

    // Simulate an address change that never reached project_member.email.
    String nextEmail = "renamed-" + UUID.randomUUID() + "@example.test";
    jdbc.update("update users set email = ? where id = ?", nextEmail, colleague.getId());
    colleague.setEmail(nextEmail);

    mvc.perform(get("/api/projects/" + project.getId()).with(as(colleague)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/projects").with(as(colleague)))
        .andExpect(jsonPath("$[?(@.id == " + project.getId() + ")]").isNotEmpty());

    // An account that later takes the old address inherits nothing.
    User newcomer = user();
    jdbc.update(
        "update project_member set email = ? where project_id = ? and user_id = ?",
        newcomer.getEmail(),
        project.getId(),
        colleague.getId());
    mvc.perform(get("/api/projects/" + project.getId()).with(as(newcomer)))
        .andExpect(status().isForbidden());

    // Removing by the current address finds the membership through the account.
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/projects/" + project.getId() + "/members/" + nextEmail)
                .with(as(owner))
                .with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/projects/" + project.getId()).with(as(colleague)))
        .andExpect(status().isForbidden());
  }

  @Test
  void notificationsFollowTheAccountIdNotTheStoredMemberEmail() throws Exception {
    User owner = user();
    User colleague = user();
    User newcomer = user();
    Project project = project(owner, colleague);
    String oldEmail = colleague.getEmail();
    String nextEmail = "renamed-" + UUID.randomUUID() + "@example.test";
    jdbc.update("update users set email = ? where id = ?", nextEmail, colleague.getId());
    colleague.setEmail(nextEmail);
    // The stale member address now belongs to someone else.
    jdbc.update("update users set email = ? where id = ?", oldEmail, newcomer.getId());
    newcomer.setEmail(oldEmail);
    String insert =
        "insert into notification(project_id, recipient_email, message, is_read, created_at)"
            + " values (?, ?, ?, false, now())";
    jdbc.update(insert, project.getId(), nextEmail.toLowerCase(), "for the member");
    jdbc.update(insert, project.getId(), oldEmail.toLowerCase(), "for the old address");

    mvc.perform(get("/api/notifications").with(as(colleague)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.message == 'for the member')]").isNotEmpty());
    mvc.perform(get("/api/notifications").with(as(newcomer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.message == 'for the old address')]").isEmpty());
  }

  @Test
  void ownershipTransferMovesTheOwnerRoleBetweenAccounts() throws Exception {
    User owner = user();
    User next = user();
    Project project = project(owner, next);
    mvc.perform(
            body(
                put("/api/projects/" + project.getId() + "/owner"),
                owner,
                "{\"email\":\"" + next.getEmail() + "\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/projects/" + project.getId() + "/role").with(as(next)))
        .andExpect(jsonPath("$.role").value("Owner"));
    mvc.perform(get("/api/projects/" + project.getId() + "/role").with(as(owner)))
        .andExpect(jsonPath("$.role").value("Member"));
  }
}
