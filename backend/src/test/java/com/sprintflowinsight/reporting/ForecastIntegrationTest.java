package com.sprintflowinsight.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.support.ApiIntegrationTest;
import com.sprintflowinsight.workspace.project.Project;
import com.sprintflowinsight.workspace.sprint.Sprint;
import com.sprintflowinsight.workspace.sprint.SprintRepository;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskNumberAllocator;
import com.sprintflowinsight.workspace.task.TaskRepository;
import com.sprintflowinsight.workspace.task.TaskStatusHistory;
import com.sprintflowinsight.workspace.task.TaskStatusHistoryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** {@code GET /api/projects/{id}/forecast}: data derivation, roles, validation and query cost. */
class ForecastIntegrationTest extends ApiIntegrationTest {
  @Autowired private TaskRepository tasks;
  @Autowired private TaskStatusHistoryRepository history;
  @Autowired private SprintRepository sprints;
  @Autowired private TaskNumberAllocator numbers;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void scopesRecordedWorkToAnEpicButKeepsProjectCapacityAndRejectsForeignEpics() throws Exception {
    User owner = user();
    Project own = project(owner, null), foreign = project(user(), null);
    completeWeeks(own, 8);
    long epic =
        jdbc.queryForObject(
            "insert into epic(project_id,title,created_at) values (?,'Release',now()) returning id",
            Long.class,
            own.getId());
    long foreignEpic =
        jdbc.queryForObject(
            "insert into epic(project_id,title,created_at) values (?,'Secret',now()) returning id",
            Long.class,
            foreign.getId());
    long story =
        jdbc.queryForObject(
            "insert into"
                + " user_stories(project_id,title,acceptance_criteria,story_points,priority_id,epic_id,version)"
                + " values (?,'Planned','Ready',3,1,?,0) returning id",
            Long.class,
            own.getId(),
            epic);
    jdbc.update(
        "insert into"
            + " user_stories(project_id,title,acceptance_criteria,story_points,priority_id,epic_id,version)"
            + " values (?,'Not split','Ready',3,1,?,0)",
        own.getId(),
        epic);
    Task scoped = task(own, "To Do", null);
    scoped.setStoryId(story);
    tasks.save(scoped);
    task(own, "To Do", null);
    mvc.perform(forecast(own).param("epicId", Long.toString(epic)).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.remaining").value(1))
        .andExpect(jsonPath("$.scope.epicTitle").value("Release"))
        .andExpect(jsonPath("$.scope.unplannedStories").value(1))
        .andExpect(jsonPath("$.history", hasSize(8)));
    mvc.perform(forecast(own).param("epicId", Long.toString(foreignEpic)).with(as(owner)))
        .andExpect(status().isNotFound());
    mvc.perform(forecast(foreign).param("epicId", Long.toString(epic)).with(as(owner)))
        .andExpect(status().isForbidden());
    jdbc.update("update epic set title='Renamed release' where id=?", epic);
    mvc.perform(forecast(own).param("epicId", Long.toString(epic)).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scope.epicTitle").value("Renamed release"));
  }

  @Test
  void ignoresFutureTransitionsAndDoneSnapshotsAndRefreshesCachedWork() throws Exception {
    User owner = user();
    Project project = project(owner, null);
    completeWeeks(project, 6);
    Long oldestId =
        history.findByProjectIdOrderByChangedAtAscIdAsc(project.getId()).stream()
            .filter(row -> "Done".equals(row.getStatus()))
            .findFirst()
            .orElseThrow()
            .getTaskId();
    Task oldest = tasks.findById(oldestId).orElseThrow();
    event(oldest, "Done", null, Instant.now().minusSeconds(2), 1);
    event(oldest, "To Do", null, Instant.now().plus(2, ChronoUnit.DAYS), 1);
    Task open = task(project, "To Do", null);
    mvc.perform(forecast(project).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.remaining").value(1))
        .andExpect(jsonPath("$.history[0].count").value(3));
    var before =
        mvc.perform(forecast(project).with(as(owner)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    open = tasks.findById(open.getId()).orElseThrow();
    open.setStatus("Done");
    tasks.save(open);
    event(open, "Done", null, Instant.now().minusSeconds(1), 1);
    var after =
        mvc.perform(forecast(project).with(as(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.remaining").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(after).isNotEqualTo(before);
  }

  @Test
  void forecastsFromWeeklyThroughputWithTenThousandBootstrapPaths() throws Exception {
    User owner = user();
    Project project = project(owner, null);
    int done = completeWeeks(project, 20);
    for (int i = 0; i < 5; i++) task(project, "To Do", null);

    mvc.perform(forecast(project).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.basis").value("THROUGHPUT"))
        .andExpect(jsonPath("$.unit").value("ITEMS"))
        .andExpect(jsonPath("$.periodDays").value(7))
        .andExpect(jsonPath("$.remaining").value(5))
        .andExpect(jsonPath("$.history", hasSize(20)))
        .andExpect(jsonPath("$.simulations").value(10000))
        .andExpect(jsonPath("$.completionDates", hasSize(3)))
        .andExpect(jsonPath("$.completionDates[0].confidence").value(0.5))
        .andExpect(jsonPath("$.completionHistogram").isArray())
        .andExpect(jsonPath("$.target.source").value("DEFAULT"));
    assertThat(done).isPositive();

    String first =
        mvc.perform(forecast(project).with(as(owner)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String second =
        mvc.perform(forecast(project).with(as(owner)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(second).as("deterministic for the same data").isEqualTo(first);
  }

  @Test
  void answersAnExplicitTargetAndDefaultsToTheActiveSprint() throws Exception {
    User owner = user();
    Project project = project(owner, null);
    completeWeeks(project, 6);
    LocalDate end = LocalDate.now(ZoneOffset.UTC).plusDays(10);
    Sprint active = new Sprint("Active sprint", "Goal", "Active", 0);
    active.setProjectId(project.getId());
    active.setStartDate(end.minusDays(13));
    active.setEndDate(end);
    active = sprints.save(active);
    for (int i = 0; i < 3; i++) task(project, "To Do", active.getId());
    task(project, "In Progress", null);

    mvc.perform(forecast(project).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.minimumPeriods").value(6))
        .andExpect(jsonPath("$.remaining").value(4))
        .andExpect(jsonPath("$.target.source").value("ACTIVE_SPRINT"))
        .andExpect(jsonPath("$.target.items").value(3))
        .andExpect(jsonPath("$.target.date").value(end.toString()))
        .andExpect(jsonPath("$.itemsByDate.items", hasSize(3)));

    String date = LocalDate.now(ZoneOffset.UTC).plusDays(30).toString();
    mvc.perform(
            forecast(project)
                .param("targetDate", date)
                .param("targetItems", "2")
                .param("remaining", "40")
                .with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.remaining").value(40))
        .andExpect(jsonPath("$.target.source").value("REQUEST"))
        .andExpect(jsonPath("$.target.items").value(2))
        .andExpect(jsonPath("$.target.probability").isNumber());
  }

  @Test
  void velocityUsesStoryPointsDeliveredPerCompletedSprint() throws Exception {
    User owner = user();
    Project project = project(owner, null);
    Instant now = Instant.now();
    for (int s = 3; s >= 1; s--) {
      Sprint sprint = new Sprint("Sprint " + s, "Goal", "Completed", 10);
      sprint.setProjectId(project.getId());
      LocalDate end = LocalDate.now(ZoneOffset.UTC).minusDays(14L * (s - 1) + 1);
      sprint.setStartDate(end.minusDays(13));
      sprint.setEndDate(end);
      sprint.setCreatedAt(now.minus(60, ChronoUnit.DAYS));
      sprint.setCompletedAt(end.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600));
      sprint = sprints.save(sprint);
      Task task = task(project, "Done", sprint.getId());
      event(task, "Done", sprint.getId(), sprint.getCompletedAt().minusSeconds(60), s + 2);
    }
    Task open = task(project, "To Do", null);
    open.setStoryPoints(8);
    tasks.save(open);

    mvc.perform(forecast(project).param("basis", "VELOCITY").with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unit").value("STORY_POINTS"))
        .andExpect(jsonPath("$.periodDays").value(14))
        .andExpect(jsonPath("$.remaining").value(8))
        .andExpect(jsonPath("$.history", hasSize(3)))
        .andExpect(jsonPath("$.history[0].label").value("Sprint 3"))
        .andExpect(jsonPath("$.history[0].count").value(5))
        .andExpect(jsonPath("$.history[2].count").value(3));
  }

  @Test
  void withoutCompletedWorkNoForecastIsMade() throws Exception {
    User owner = user();
    Project project = project(owner, null);
    task(project, "To Do", null);

    mvc.perform(forecast(project).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.warnings[0]").value("NO_HISTORY"))
        .andExpect(jsonPath("$.simulations").value(0))
        .andExpect(jsonPath("$.target.probability").doesNotExist())
        .andExpect(jsonPath("$.completionDates", hasSize(3)))
        .andExpect(jsonPath("$.completionDates[0].date").doesNotExist())
        .andExpect(jsonPath("$.completionHistogram", hasSize(0)));
  }

  @Test
  void everyMemberRoleMayReadButOutsidersMayNot() throws Exception {
    User owner = user();
    Project project = project(owner, null);
    completeWeeks(project, 3);
    for (String role : new String[] {"Admin", "Member", "Viewer"}) {
      User member = user();
      member(project, member, role);
      mvc.perform(forecast(project).with(as(member))).andExpect(status().isOk());
    }
    mvc.perform(forecast(project).with(as(owner))).andExpect(status().isOk());
    mvc.perform(forecast(project).with(as(user())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mvc.perform(forecast(project)).andExpect(status().isUnauthorized());

    project = projects.findById(project.getId()).orElseThrow();
    project.setArchivedAt(Instant.now());
    projects.save(project);
    mvc.perform(forecast(project).with(as(owner))).andExpect(status().isOk());

    project = projects.findById(project.getId()).orElseThrow();
    project.setTrashedAt(Instant.now());
    projects.save(project);
    mvc.perform(forecast(project).with(as(owner)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void rejectsInvalidParametersAsProblems() throws Exception {
    User owner = user();
    Project project = project(owner, null);
    String past = LocalDate.now(ZoneOffset.UTC).minusDays(1).toString();
    String far = LocalDate.now(ZoneOffset.UTC).plusYears(6).toString();

    mvc.perform(forecast(project).param("remaining", "-1").with(as(owner)))
        .andExpect(status().isBadRequest());
    mvc.perform(forecast(project).param("remaining", "10001").with(as(owner)))
        .andExpect(status().isBadRequest());
    mvc.perform(forecast(project).param("epicId", "0").with(as(owner)))
        .andExpect(status().isBadRequest());
    mvc.perform(forecast(project).param("remaining", "1.5").with(as(owner)))
        .andExpect(status().isBadRequest());
    mvc.perform(forecast(project).param("basis", "GUESS").with(as(owner)))
        .andExpect(status().isBadRequest());
    mvc.perform(forecast(project).param("targetItems", "3").with(as(owner)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
    mvc.perform(forecast(project).param("targetDate", past).with(as(owner)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    mvc.perform(forecast(project).param("targetDate", far).with(as(owner)))
        .andExpect(status().isBadRequest());
    mvc.perform(forecast(project).param("targetDate", "not-a-date").with(as(owner)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void costsTheSameNumberOfQueriesForSmallAndLargeProjects() throws Exception {
    User owner = user();
    Project small = project(owner, null);
    completeWeeks(small, 2);
    Project large = project(owner, null);
    completeWeeks(large, 30);
    for (int i = 0; i < 1000; i++) task(large, "To Do", null);

    long smallCost = statementsFor(() -> mvc.perform(forecast(small).with(as(owner))));
    long largeCost = statementsFor(() -> mvc.perform(forecast(large).with(as(owner))));
    assertThat(largeCost).isEqualTo(smallCost);
    // user lookup + access row + tasks + status history + sprints + stories
    assertThat(largeCost).isLessThanOrEqualTo(8);
  }

  @Test
  void rejectsOversizedAutomaticWorkWithoutSilentlyTruncatingIt() throws Exception {
    User owner = user();
    Project project = project(owner, null);
    for (int i = 0; i < 21; i++) {
      Task open = task(project, "To Do", null);
      open.setStoryPoints(500);
      tasks.save(open);
    }
    mvc.perform(forecast(project).param("basis", "VELOCITY").with(as(owner)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    mvc.perform(
            forecast(project).param("basis", "VELOCITY").param("remaining", "10").with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.remaining").value(10))
        .andExpect(jsonPath("$.scope.openStoryPoints").value(10500));
  }

  @Test
  void backtestingUsesCurrentProjectHistoryAndRequiresMembership() throws Exception {
    User owner = user();
    Project own = project(owner, null);
    User admin = user(), member = user(), viewer = user(), outsider = user();
    member(own, admin, "Admin");
    member(own, member, "Member");
    member(own, viewer, "Viewer");
    String path = "/api/projects/" + own.getId() + "/forecast/backtest";
    mvc.perform(get(path).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INSUFFICIENT_HISTORY"))
        .andExpect(jsonPath("$.requiredPeriods").value(15));
    completeWeeks(own, 18);
    var report =
        mvc.perform(get(path).with(as(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SELECTED"))
            .andExpect(jsonPath("$.availablePeriods").value(18))
            .andExpect(jsonPath("$.candidates", hasSize(3)))
            .andExpect(jsonPath("$.candidates[0].origins").value(9))
            .andExpect(jsonPath("$.candidates[0].pitHistogram", hasSize(10)))
            .andExpect(jsonPath("$.prediction.intervals", hasSize(3)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    for (User reader : new User[] {admin, member, viewer}) {
      assertThat(
              mvc.perform(get(path).with(as(reader)))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString())
          .isEqualTo(report);
    }
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(get(path).with(as(outsider))).andExpect(status().isForbidden());
    mvc.perform(get(path).param("basis", "OTHER").with(as(owner)))
        .andExpect(status().isBadRequest());
    mvc.perform(get(path).param("basis", "VELOCITY").with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unit").value("STORY_POINTS"))
        .andExpect(jsonPath("$.status").value("INSUFFICIENT_HISTORY"));

    jdbc.update("update projects set archived_at=now() where id=?", own.getId());
    mvc.perform(get(path).with(as(viewer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SELECTED"));
    jdbc.update("update projects set trashed_at=now() where id=?", own.getId());
    mvc.perform(get(path).with(as(owner))).andExpect(status().isNotFound());
  }

  private MockHttpServletRequestBuilder forecast(Project project) {
    return get("/api/projects/" + project.getId() + "/forecast");
  }

  /** Completes 1–4 tasks in each of the {@code weeks} full weeks before this one. */
  private int completeWeeks(Project project, int weeks) {
    int total = 0;
    Instant now = Instant.now();
    for (int week = 1; week <= weeks; week++) {
      for (int i = 0; i <= week % 4; i++) {
        Task task = task(project, "Done", null);
        if (week == weeks && i == 0) {
          var firstDay =
              LocalDate.ofInstant(now.minus(7L * week, ChronoUnit.DAYS), ZoneOffset.UTC)
                  .with(
                      java.time.temporal.TemporalAdjusters.previousOrSame(
                          java.time.DayOfWeek.MONDAY));
          event(task, "To Do", null, firstDay.atStartOfDay(ZoneOffset.UTC).toInstant(), 1);
        }
        event(task, "Done", null, now.minus(7L * week, ChronoUnit.DAYS), 1);
        total++;
      }
    }
    return total;
  }

  private Task task(Project project, String status, Long sprintId) {
    Task task = new Task("Task", null, status);
    task.setProjectId(project.getId());
    task.setNumber(numbers.next(project.getId()).number());
    task.setSprintId(sprintId);
    return tasks.save(task);
  }

  private void event(Task task, String status, Long sprintId, Instant at, int points) {
    TaskStatusHistory event = new TaskStatusHistory();
    event.setTaskId(task.getId());
    event.setProjectId(task.getProjectId());
    event.setSprintId(sprintId);
    event.setStatus(status);
    event.setChangedAt(at);
    event.setStoryPoints(points);
    history.save(event);
  }
}
