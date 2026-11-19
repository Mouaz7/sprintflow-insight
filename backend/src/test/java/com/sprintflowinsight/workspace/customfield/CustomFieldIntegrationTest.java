package com.sprintflowinsight.workspace.customfield;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.support.ApiIntegrationTest;
import com.sprintflowinsight.workspace.project.Project;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class CustomFieldIntegrationTest extends ApiIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void roundTripsEveryTypeWithExactDecimalsAndTaskRevisionAndFiltersWithoutWorkflowChanges()
      throws Exception {
    User owner = user();
    Project project = project(owner, null);
    long task = task(project), empty = task(project);
    long text = definition(project, owner, "Reference", "TEXT", "[]");
    long number = definition(project, owner, "Budget", "NUMBER", "[]");
    long date = definition(project, owner, "Due", "DATE", "[]");
    long select = definition(project, owner, "Stage", "SELECT", "[\"Ready\",\"Later\"]");
    long user = definition(project, owner, "Reviewer", "USER", "[]");
    long member =
        members.findByProjectIdAndUserId(project.getId(), owner.getId()).orElseThrow().getId();
    String body =
        """
        {"values":[{"fieldId":%d,"value":"Literal %%_Ö"},
          {"fieldId":%d,"value":"9007199254740993.100000"},
          {"fieldId":%d,"value":"2026-11-18"},{"fieldId":%d,"value":"Ready"},
          {"fieldId":%d,"value":"%d"}]}
        """
            .formatted(text, number, date, select, user, member);
    mvc.perform(write(put(values(task)), owner, body).header("If-Match", "\"0\""))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"1\""))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.values[1].value").value("9007199254740993.1"))
        .andExpect(jsonPath("$.values[4].displayValue").value(owner.getEmail()));
    mvc.perform(write(put(values(task)), owner, body).header("If-Match", "\"0\""))
        .andExpect(status().isPreconditionFailed());
    mvc.perform(write(put(values(task)), owner, body).header("If-Match", "\"1\""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from task_status_history where task_id=?", Long.class, task))
        .isZero();
    mvc.perform(
            get(base(project) + "/matches")
                .with(as(owner))
                .param("fieldId", Long.toString(number))
                .param("operator", "GREATER_THAN")
                .param("value", "9007199254740993"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taskIds[0]").value(task));
    mvc.perform(
            get(base(project) + "/matches")
                .with(as(owner))
                .param("fieldId", Long.toString(text))
                .param("operator", "CONTAINS")
                .param("value", "%_Ö"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taskIds[0]").value(task));
    mvc.perform(
            get(base(project) + "/matches")
                .with(as(owner))
                .param("fieldId", Long.toString(text))
                .param("operator", "CONTAINS")
                .param("value", "%_ö"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taskIds").isEmpty());
    mvc.perform(
            get(base(project) + "/matches")
                .with(as(owner))
                .param("fieldId", Long.toString(user))
                .param("operator", "IS_NOT_SET"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taskIds[0]").value(empty));
    mvc.perform(
            write(
                    put(base(project) + "/" + select),
                    owner,
                    "{\"name\":\"Stage\",\"kind\":\"SELECT\",\"options\":[\"Later\"]}")
                .header("If-Match", "\"0\""))
        .andExpect(status().isConflict());
    mvc.perform(write(put(values(task)), owner, "{\"values\":[]}").header("If-Match", "\"1\""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.values").isEmpty());
  }

  @Test
  void permissionsDistinguishDefinitionsFromValuesAndRespectArchiveAndTrash() throws Exception {
    User owner = user(), admin = user(), member = user(), viewer = user(), outsider = user();
    Project project = project(owner, member);
    member(project, admin, "Admin");
    member(project, viewer, "Viewer");
    long field = definition(project, owner, "Reference", "TEXT", "[]"), task = task(project);
    String body = "{\"values\":[{\"fieldId\":" + field + ",\"value\":\"Saved\"}]}";
    for (User reader : new User[] {owner, admin, member, viewer}) {
      mvc.perform(get(base(project)).with(as(reader))).andExpect(status().isOk());
      mvc.perform(get(values(task)).with(as(reader))).andExpect(status().isOk());
    }
    definition(project, admin, "Admin field", "NUMBER", "[]");
    for (User denied : new User[] {member, viewer})
      mvc.perform(write(post(base(project)), denied, "{\"name\":\"Forbidden\",\"kind\":\"TEXT\"}"))
          .andExpect(status().isForbidden());
    mvc.perform(write(put(values(task)), viewer, body).header("If-Match", "\"0\""))
        .andExpect(status().isForbidden());
    mvc.perform(write(put(values(task)), member, body).header("If-Match", "\"0\""))
        .andExpect(status().isOk());
    mvc.perform(get(base(project)).with(as(outsider))).andExpect(status().isForbidden());
    mvc.perform(get(values(task)).with(as(outsider))).andExpect(status().isForbidden());
    mvc.perform(get(base(project))).andExpect(status().isUnauthorized());
    project = projects.findById(project.getId()).orElseThrow();
    project.setArchivedAt(Instant.now());
    projects.saveAndFlush(project);
    for (User reader : new User[] {owner, admin, member, viewer})
      mvc.perform(get(values(task)).with(as(reader))).andExpect(status().isOk());
    mvc.perform(write(put(values(task)), owner, body).header("If-Match", "\"1\""))
        .andExpect(status().isConflict());
    mvc.perform(write(post(base(project)), admin, "{\"name\":\"No\",\"kind\":\"TEXT\"}"))
        .andExpect(status().isConflict());
    project = projects.findById(project.getId()).orElseThrow();
    project.setTrashedAt(Instant.now());
    projects.saveAndFlush(project);
    mvc.perform(get(base(project)).with(as(owner))).andExpect(status().isNotFound());
    mvc.perform(get(values(task)).with(as(owner))).andExpect(status().isNotFound());
  }

  @Test
  void rejectsInvalidSnapshotsWithoutPartialChangesAndVersionsDefinitionEdits() throws Exception {
    User owner = user();
    Project project = project(owner, null), other = project(owner, null);
    long task = task(project), field = definition(project, owner, "Budget", "NUMBER", "[]");
    long foreign = definition(other, owner, "Foreign", "TEXT", "[]");
    for (String body :
        java.util.List.of(
            "{\"values\":[{\"fieldId\":"
                + field
                + ",\"value\":\"2\"},{\"fieldId\":"
                + field
                + ",\"value\":\"3\"}]}",
            "{\"values\":[{\"fieldId\":" + field + ",\"value\":\"NaN\"}]}")) {
      mvc.perform(write(put(values(task)), owner, body).header("If-Match", "\"0\""))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(
            write(
                    put(values(task)),
                    owner,
                    "{\"values\":[{\"fieldId\":"
                        + field
                        + ",\"value\":\"2\"},{\"fieldId\":"
                        + foreign
                        + ",\"value\":\"no\"}]}")
                .header("If-Match", "\"0\""))
        .andExpect(status().isNotFound());
    mvc.perform(get(values(task)).with(as(owner)))
        .andExpect(jsonPath("$.version").value(0))
        .andExpect(jsonPath("$.values").isEmpty());
    String renamed = "{\"name\":\"Cost\",\"kind\":\"NUMBER\",\"options\":[]}";
    mvc.perform(write(put(base(project) + "/" + field), owner, renamed))
        .andExpect(status().isPreconditionRequired());
    mvc.perform(write(put(base(project) + "/" + field), owner, renamed).header("If-Match", "\"0\""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(
            delete(base(project) + "/" + field)
                .with(as(owner))
                .with(csrf())
                .header("If-Match", "\"0\""))
        .andExpect(status().isPreconditionFailed());
    mvc.perform(
            write(
                    put(base(project) + "/" + field),
                    owner,
                    "{\"name\":\"Cost\",\"kind\":\"TEXT\",\"options\":[]}")
                .header("If-Match", "\"1\""))
        .andExpect(status().isConflict());
    for (int n = 1; n < 20; n++) definition(project, owner, "Extra " + n, "TEXT", "[]");
    mvc.perform(
            write(
                post(base(project)),
                owner,
                "{\"name\":\"Too many\",\"kind\":\"TEXT\",\"options\":[]}"))
        .andExpect(status().isConflict());
  }

  @Test
  void serializesConcurrentValueSnapshotsSoOnlyOneWriterWinsTheSharedTaskRevision()
      throws Exception {
    User owner = user();
    Project project = project(owner, null);
    long task = task(project), field = definition(project, owner, "Reference", "TEXT", "[]");
    var ready = new java.util.concurrent.CountDownLatch(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var calls = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
      for (String value : java.util.List.of("First", "Second"))
        calls.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    throw new AssertionError("Start timeout");
                  return mvc.perform(
                          write(
                                  put(values(task)),
                                  owner,
                                  "{\"values\":[{\"fieldId\":"
                                      + field
                                      + ",\"value\":\""
                                      + value
                                      + "\"}]}")
                              .header("If-Match", "\"0\""))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(
              java.util.List.of(
                  calls.get(0).get(20, java.util.concurrent.TimeUnit.SECONDS),
                  calls.get(1).get(20, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 412);
    }
    mvc.perform(get(values(task)).with(as(owner)))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.values.length()").value(1));
  }

  @Test
  void exportsThousandTaskValuesWithBoundedQueriesRatherThanOneQueryPerTask() throws Exception {
    User owner = user();
    Project project = project(owner, null);
    long field = definition(project, owner, "Reference", "TEXT", "[]");
    jdbc.update(
        "insert into"
            + " task(project_id,number,title,status,is_blocked,story_points,version,created_at)"
            + " select ?, n, 'Bulk ' || n, 'To Do', false, 1, 0, now() from generate_series(1,1000)"
            + " n",
        project.getId());
    jdbc.update(
        "insert into task_custom_value(project_id,task_id,field_id,kind,text_value) select ?, id,"
            + " ?, 'TEXT', 'Bulk value' from task where project_id=?",
        project.getId(),
        field,
        project.getId());
    long queries =
        statementsFor(
            () ->
                mvc.perform(get("/api/projects/" + project.getId() + "/export.csv").with(as(owner)))
                    .andExpect(status().isOk())
                    .andExpect(
                        content().string(org.hamcrest.Matchers.containsString("Bulk 1000"))));
    assertThat(queries)
        .as("1000 rows use bulk definitions and a streamed value cursor")
        .isLessThanOrEqualTo(20);
    long matches =
        statementsFor(
            () ->
                mvc.perform(
                        get(base(project) + "/matches")
                            .with(as(owner))
                            .param("fieldId", Long.toString(field))
                            .param("operator", "EQUALS")
                            .param("value", "Bulk value"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.taskIds.length()").value(1000)));
    assertThat(matches).isLessThanOrEqualTo(8);
  }

  @Test
  void everyEndpointChecksEveryRoleBeforeLookupAndBlocksArchivedWrites() throws Exception {
    for (boolean archived : new boolean[] {false, true}) {
      User owner = user();
      Project project = project(owner, null);
      long field = definition(project, owner, "Reference", "TEXT", "[]"), task = task(project);
      if (archived) {
        project.setArchivedAt(Instant.now());
        projects.saveAndFlush(project);
      }
      for (String role : java.util.List.of("Owner", "Admin", "Member", "Viewer", "Outsider")) {
        User caller = role.equals("Owner") ? owner : user();
        if (!role.equals("Owner") && !role.equals("Outsider")) member(project, caller, role);
        boolean reader = !role.equals("Outsider");
        int read = reader ? 200 : 403;
        mvc.perform(get(base(project)).with(as(caller))).andExpect(status().is(read));
        mvc.perform(get(values(task)).with(as(caller))).andExpect(status().is(read));
        mvc.perform(
                get(base(project) + "/matches")
                    .with(as(caller))
                    .param("fieldId", Long.toString(field))
                    .param("operator", "IS_SET"))
            .andExpect(status().is(read));
        boolean admin = role.equals("Owner") || role.equals("Admin");
        int manage = !admin ? 403 : archived ? 409 : 200;
        mvc.perform(
                write(
                    post(base(project)),
                    caller,
                    "{\"name\":\"New " + role + "\",\"kind\":\"TEXT\",\"options\":[]}"))
            .andExpect(status().is(manage));
        long version =
            jdbc.queryForObject("select version from custom_field where id=?", Long.class, field);
        mvc.perform(
                write(
                        put(base(project) + "/" + field),
                        caller,
                        "{\"name\":\"Rename " + role + "\",\"kind\":\"TEXT\",\"options\":[]}")
                    .header("If-Match", "\"" + version + "\""))
            .andExpect(status().is(manage));
        // Deleting an unknown id proves authorization precedes scoped definition lookup.
        mvc.perform(
                delete(base(project) + "/999999999")
                    .with(as(caller))
                    .with(csrf())
                    .header("If-Match", "\"0\""))
            .andExpect(status().is(!admin ? 403 : archived ? 409 : 404));
        long taskVersion =
            jdbc.queryForObject("select version from task where id=?", Long.class, task);
        int write = !reader || role.equals("Viewer") ? 403 : archived ? 409 : 200;
        mvc.perform(
                write(
                        put(values(task)),
                        caller,
                        "{\"values\":[{\"fieldId\":" + field + ",\"value\":\"" + role + "\"}]}")
                    .header("If-Match", "\"" + taskVersion + "\""))
            .andExpect(status().is(write));
      }
    }
  }

  private long definition(Project project, User owner, String name, String kind, String options)
      throws Exception {
    String content =
        "{\"name\":\"" + name + "\",\"kind\":\"" + kind + "\",\"options\":" + options + "}";
    JsonNode result =
        json.readTree(
            mvc.perform(write(post(base(project)), owner, content))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    return result.get("id").asLong();
  }

  private long task(Project project) {
    Integer number =
        jdbc.queryForObject(
            "select coalesce(max(number),0)+1 from task where project_id=?",
            Integer.class,
            project.getId());
    return jdbc.queryForObject(
        "insert into"
            + " task(project_id,number,title,status,is_blocked,story_points,version,created_at)"
            + " values (?,?,'Custom field task','To Do',false,1,0,now()) returning id",
        Long.class,
        project.getId(),
        number);
  }

  private String base(Project project) {
    return "/api/projects/" + project.getId() + "/custom-fields";
  }

  private String values(long task) {
    return "/api/tasks/" + task + "/custom-fields";
  }

  private MockHttpServletRequestBuilder write(
      MockHttpServletRequestBuilder request, User user, String body) {
    return request
        .with(as(user))
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }
}
