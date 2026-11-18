package com.sprintflowinsight.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sprintflowinsight.support.ApiIntegrationTest;
import com.sprintflowinsight.workspace.project.Project;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

class FlowIntegrationTest extends ApiIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  private final JsonMapper json = JsonMapper.builder().build();

  private String base(Project project) {
    return "/api/projects/" + project.getId();
  }

  private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object value) {
    return request
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(value));
  }

  private Map<String, Object> limit(int count) {
    return Map.of("limits", List.of(Map.of("status", "In Progress", "limit", count)));
  }

  private long task(Project project, int number, String status, Instant created) {
    return jdbc.queryForObject(
        "insert into"
            + " task(project_id,number,title,status,is_blocked,story_points,created_at,version)"
            + " values (?,?,?, ?,false,1,?,0) returning id",
        Long.class,
        project.getId(),
        number,
        "Flow task " + number,
        status,
        java.sql.Timestamp.from(created));
  }

  private void event(Project project, long task, String status, Instant at) {
    jdbc.update(
        "insert into"
            + " task_status_history(task_id,project_id,status,changed_at,actor_email,story_points)"
            + " values (?,?,?,?,?,1)",
        task,
        project.getId(),
        status,
        java.sql.Timestamp.from(at),
        project.getOwnerEmail());
  }

  @ParameterizedTest
  @ValueSource(strings = {"Owner", "Admin", "Member", "Viewer", "Outsider", "Archived", "Trashed"})
  void everyEndpointEnforcesMembershipAndManagementRoles(String role) throws Exception {
    var owner = user();
    var project = project(owner, null);
    var caller = owner;
    if (List.of("Admin", "Member", "Viewer", "Outsider").contains(role)) {
      caller = user();
      if (!role.equals("Outsider")) member(project, caller, role);
    }
    if (role.equals("Archived")) {
      project.setArchivedAt(Instant.now());
      projects.save(project);
    }
    if (role.equals("Trashed")) {
      project.setTrashedAt(Instant.now());
      projects.save(project);
    }
    int read = role.equals("Outsider") ? 403 : role.equals("Trashed") ? 404 : 200;
    for (String resource : List.of("flow", "wip-limits"))
      mvc.perform(get(base(project) + "/" + resource).with(as(caller)))
          .andExpect(status().is(read));
    int write =
        role.equals("Trashed")
            ? 404
            : role.equals("Archived") ? 409 : List.of("Owner", "Admin").contains(role) ? 200 : 403;
    mvc.perform(
            body(put(base(project) + "/wip-limits").header("If-Match", "\"0\""), limit(2))
                .with(as(caller)))
        .andExpect(status().is(write));
  }

  @Test
  void incompleteHistoryAndSameStatusSnapshotsDoNotInventDurations() throws Exception {
    var owner = user();
    var project = project(owner, null);
    var now = Instant.now();
    long done = task(project, 1, "Done", now.minus(6, ChronoUnit.DAYS));
    event(project, done, "To Do", now.minus(6, ChronoUnit.DAYS));
    event(project, done, "In Progress", now.minus(3, ChronoUnit.DAYS));
    event(project, done, "Done", now.minus(2, ChronoUnit.DAYS));
    event(project, done, "Done", now.minus(1, ChronoUnit.DAYS));
    long active = task(project, 2, "Review", now.minus(6, ChronoUnit.DAYS));
    event(project, active, "In Progress", now.minus(2, ChronoUnit.DAYS));
    event(project, active, "Review", now.minus(1, ChronoUnit.DAYS));
    event(project, active, "Review", now.minus(1, ChronoUnit.HOURS));
    task(project, 3, "To Do", now.minus(5, ChronoUnit.DAYS));
    long trashed = task(project, 4, "Done", now.minus(5, ChronoUnit.DAYS));
    event(project, trashed, "In Progress", now.minus(4, ChronoUnit.DAYS));
    event(project, trashed, "Done", now.minus(3, ChronoUnit.DAYS));
    jdbc.update("update task set trashed_at=now() where id=?", trashed);
    mvc.perform(get(base(project) + "/flow?days=7").with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completedTasks").value(1))
        .andExpect(jsonPath("$.cycleTimeHours.p50").value(24.0))
        .andExpect(jsonPath("$.leadTimeHours.p95").value(96.0))
        .andExpect(jsonPath("$.agingWip.length()").value(1))
        .andExpect(jsonPath("$.agingWip[0].taskId").value(active))
        .andExpect(
            jsonPath("$.agingWip[0].statusAgeHours").value(org.hamcrest.Matchers.closeTo(24, .05)))
        .andExpect(jsonPath("$.tasksWithoutHistory").value(1))
        .andExpect(jsonPath("$.columns[0].currentWip").value(1))
        .andExpect(jsonPath("$.controlChart.available").value(false))
        .andExpect(jsonPath("$.littlesLaw.status").value("INSUFFICIENT_DATA"));
  }

  @Test
  void staleSettingsCannotOverwriteAChangeOrReuseAClearedRevision() throws Exception {
    var owner = user();
    var project = project(owner, null);
    String url = base(project) + "/wip-limits";
    mvc.perform(get(url).with(as(owner))).andExpect(jsonPath("$.version").value(0));
    mvc.perform(body(put(url), limit(2)).with(as(owner)))
        .andExpect(status().isPreconditionRequired());
    mvc.perform(body(put(url).header("If-Match", "\"0\""), limit(2)).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(body(put(url).header("If-Match", "\"0\""), limit(3)).with(as(owner)))
        .andExpect(status().isPreconditionFailed());
    mvc.perform(
            body(put(url).header("If-Match", "\"1\""), Map.of("limits", List.of())).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.limits[1].limit").isEmpty());
    mvc.perform(body(put(url).header("If-Match", "\"0\""), limit(3)).with(as(owner)))
        .andExpect(status().isPreconditionFailed());
  }

  @Test
  void concurrentAdministratorsCannotBothSaveTheSameRevision() throws Exception {
    var owner = user();
    var project = project(owner, null);
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var responses =
          List.of(1, 2).stream()
              .map(
                  count ->
                      pool.submit(
                          () -> {
                            ready.countDown();
                            if (!start.await(10, TimeUnit.SECONDS))
                              throw new IllegalStateException("No start signal");
                            return mvc.perform(
                                    body(
                                            put(base(project) + "/wip-limits")
                                                .header("If-Match", "\"0\""),
                                            limit(count))
                                        .with(as(owner)))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                          }))
              .toList();
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(
              List.of(
                  responses.get(0).get(15, TimeUnit.SECONDS),
                  responses.get(1).get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 412);
    }
  }

  @Test
  void malformedLimitsWindowsAndAnonymousReadsAreRejected() throws Exception {
    var owner = user();
    var project = project(owner, null);
    mvc.perform(get(base(project) + "/flow")).andExpect(status().isUnauthorized());
    mvc.perform(get(base(project) + "/wip-limits")).andExpect(status().isUnauthorized());
    for (String days : List.of("6", "181", "nope"))
      mvc.perform(get(base(project) + "/flow?days=" + days).with(as(owner)))
          .andExpect(status().isBadRequest());
    for (Object invalid :
        List.of(
            Map.of("limits", List.of(Map.of("status", "Done", "limit", 2))),
            limit(0),
            limit(1000),
            Map.of("limits", List.of(Map.of("status", "In Progress", "limit", 1.5))),
            Map.of(
                "limits",
                List.of(
                    Map.of("status", "Review", "limit", 1),
                    Map.of("status", "Review", "limit", 2)))))
      mvc.perform(
              body(put(base(project) + "/wip-limits").header("If-Match", "\"0\""), invalid)
                  .with(as(owner)))
          .andExpect(status().isBadRequest());
  }

  @Test
  void queryCountStaysConstantWithAThousandAdditionalTasks() throws Exception {
    var owner = user();
    var project = project(owner, null);
    var created = Instant.now().minus(2, ChronoUnit.DAYS);
    long first = task(project, 1, "To Do", created);
    event(project, first, "To Do", created);
    long small =
        statementsFor(
            () ->
                mvc.perform(get(base(project) + "/flow").with(as(owner)))
                    .andExpect(status().isOk()));
    jdbc.update(
        "insert into"
            + " task(project_id,number,title,status,is_blocked,story_points,created_at,version)"
            + " select ?,n+1,'Bulk '||n,'To Do',false,1,now()-interval '2 days',0 from"
            + " generate_series(1,1000) n",
        project.getId());
    jdbc.update(
        "insert into"
            + " task_status_history(task_id,project_id,status,changed_at,actor_email,story_points)"
            + " select id,project_id,status,created_at,?,story_points from task where project_id=?"
            + " and id<>?",
        owner.getEmail(),
        project.getId(),
        first);
    long large =
        statementsFor(
            () ->
                mvc.perform(get(base(project) + "/flow").with(as(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.columns[0].currentWip").value(1001)));
    assertThat(large).isLessThanOrEqualTo(small + 1).isLessThanOrEqualTo(12);
  }
}
