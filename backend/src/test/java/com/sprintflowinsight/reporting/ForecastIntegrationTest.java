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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** {@code GET /api/projects/{id}/forecast}: data derivation, roles, validation and query cost. */
class ForecastIntegrationTest extends ApiIntegrationTest {
  @Autowired private TaskRepository tasks;
  @Autowired private TaskStatusHistoryRepository history;
  @Autowired private SprintRepository sprints;
  @Autowired private TaskNumberAllocator numbers;

  @Test
  void forecastsFromWeeklyThroughputWithABacktestedModelChoice() throws Exception {
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
        .andExpect(jsonPath("$.model.status").value("SELECTED"))
        .andExpect(jsonPath("$.model.backtest", hasSize(3)))
        .andExpect(jsonPath("$.model.backtest[0].selected").value(true))
        .andExpect(jsonPath("$.completionDates", hasSize(3)))
        .andExpect(jsonPath("$.completionDates[0].confidence").value(0.5))
        .andExpect(jsonPath("$.fan", hasSize(ForecastCalculator.FAN_PERIODS)))
        .andExpect(jsonPath("$.target").doesNotExist());
    assertThat(done).isPositive();

    String first =
        mvc.perform(forecast(project).with(as(owner))).andReturn().getResponse().getContentAsString();
    String second =
        mvc.perform(forecast(project).with(as(owner))).andReturn().getResponse().getContentAsString();
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
        .andExpect(jsonPath("$.model.status").value("INSUFFICIENT_HISTORY"))
        .andExpect(jsonPath("$.model.name").value("gamma-poisson"))
        .andExpect(jsonPath("$.model.requiredPeriods").value(15))
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
        .andExpect(jsonPath("$.model.status").value("NO_HISTORY"))
        .andExpect(jsonPath("$.completionDates", hasSize(0)))
        .andExpect(jsonPath("$.fan", hasSize(0)));
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

    project.setArchivedAt(Instant.now());
    projects.save(project);
    mvc.perform(forecast(project).with(as(owner))).andExpect(status().isOk());

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
    for (int i = 0; i < 20; i++) task(large, "To Do", null);

    long smallCost = statementsFor(() -> mvc.perform(forecast(small).with(as(owner))));
    long largeCost = statementsFor(() -> mvc.perform(forecast(large).with(as(owner))));
    assertThat(largeCost).isEqualTo(smallCost);
    // user lookup + access row + tasks + status history + sprints
    assertThat(largeCost).isLessThanOrEqualTo(5);
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
