package com.sprintflowinsight.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.planning.optimizer.CandidateStory;
import com.sprintflowinsight.planning.optimizer.GreedySprintPlanSolver;
import com.sprintflowinsight.planning.optimizer.Lock;
import com.sprintflowinsight.planning.optimizer.PlanningProblem;
import com.sprintflowinsight.support.ApiIntegrationTest;
import com.sprintflowinsight.workspace.project.Project;
import com.sprintflowinsight.workspace.sprint.Sprint;
import com.sprintflowinsight.workspace.sprint.SprintRepository;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskRepository;
import com.sprintflowinsight.workspace.time.CapacityService;
import com.sprintflowinsight.workspace.time.CapacityUnit;
import com.sprintflowinsight.workspace.time.OptimizerCapacity;
import com.sprintflowinsight.workspace.time.TimeEntry;
import com.sprintflowinsight.workspace.time.TimeEntryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** B4 through the real HTTP layer: roles, timer, capacity, report and query cost. */
class TimeTrackingIntegrationTest extends ApiIntegrationTest {
  private static final AtomicInteger NUMBERS = new AtomicInteger(1);

  /** Monday 2 March to Friday 13 March 2026: ten working days. */
  private static final LocalDate START = LocalDate.of(2026, 3, 2);

  private static final LocalDate END = LocalDate.of(2026, 3, 13);

  @Autowired private SprintRepository sprints;
  @Autowired private TaskRepository tasks;
  @Autowired private TimeEntryRepository entries;
  @Autowired private CapacityService capacityService;

  /** Owner, admin, member, viewer and outsider around one project with a sprint and a task. */
  private final class Team {
    final User owner = user();
    final User admin = user();
    final User member = user();
    final User viewer = user();
    final User outsider = user();
    final Project project = project(owner, member);
    final Sprint sprint;
    final Task task;

    Team() {
      member(project, admin, "Admin");
      member(project, viewer, "Viewer");
      sprint = sprint(project);
      task = task(project, sprint, member, 3, 120);
    }

    String base() {
      return "/api/projects/" + project.getId();
    }

    void archive(boolean archived) {
      Project fresh = projects.findById(project.getId()).orElseThrow();
      fresh.setArchivedAt(archived ? Instant.now() : null);
      projects.save(fresh);
    }
  }

  @Test
  void capacityCanBeSetByTheMemberThemselvesOrAnOwnerOrAdminOnly() throws Exception {
    Team team = new Team();
    String member = capacityPath(team, team.member);

    mvc.perform(json(put(member), team.member, capacity(480, 100, null)))
        .andExpect(status().isOk());
    String version = memberVersion(team, team.member);
    mvc.perform(json(put(member), team.admin, capacity(420, 80, 8)).header("If-Match", version))
        .andExpect(status().isOk())
        .andExpect(jsonPath(memberRow(team.member) + ".minutesPerDay").value(420));
    version = memberVersion(team, team.member);
    mvc.perform(json(put(member), team.owner, capacity(360, 80, 8)).header("If-Match", version))
        .andExpect(status().isOk());

    // A member cannot set someone else's capacity, viewers and outsiders cannot set any.
    mvc.perform(json(put(capacityPath(team, team.admin)), team.member, capacity(480, 100, null)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mvc.perform(json(put(capacityPath(team, team.viewer)), team.viewer, capacity(480, 100, null)))
        .andExpect(status().isForbidden());
    mvc.perform(json(put(member), team.outsider, capacity(480, 100, null)))
        .andExpect(status().isForbidden());
    // Viewers carry no work, so not even an owner can give them capacity.
    mvc.perform(json(put(capacityPath(team, team.viewer)), team.owner, capacity(480, 100, null)))
        .andExpect(status().isBadRequest());
    // Stale and missing versions.
    mvc.perform(json(put(member), team.member, capacity(480, 100, null)))
        .andExpect(status().isPreconditionRequired());
    mvc.perform(
            json(put(member), team.member, capacity(480, 100, null)).header("If-Match", "\"0\""))
        .andExpect(status().isPreconditionFailed());
    // Everyone in the project, viewers included, can read the view; outsiders cannot.
    mvc.perform(
            get(team.base() + "/sprints/" + team.sprint.getId() + "/capacity")
                .with(as(team.viewer)))
        .andExpect(status().isOk());
    mvc.perform(
            get(team.base() + "/sprints/" + team.sprint.getId() + "/capacity")
                .with(as(team.outsider)))
        .andExpect(status().isForbidden());

    team.archive(true);
    version = memberVersion(team, team.member);
    mvc.perform(json(put(member), team.owner, capacity(480, 100, null)).header("If-Match", version))
        .andExpect(status().isConflict());
    mvc.perform(
            get(team.base() + "/sprints/" + team.sprint.getId() + "/capacity")
                .with(as(team.member)))
        .andExpect(status().isOk());
  }

  @Test
  void timeEntriesFollowTheSamePersonOrManagerRule() throws Exception {
    Team team = new Team();
    String entries = team.base() + "/time-entries";

    MvcResult created =
        mvc.perform(json(post(entries), team.member, entry(team.task, 90, null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.minutes").value(90))
            .andExpect(jsonPath("$.userEmail").value(team.member.getEmail()))
            .andExpect(
                jsonPath("$.taskKey").value(team.project.getKey() + "-" + team.task.getNumber()))
            .andReturn();
    long id = id(created);
    String path = entries + "/" + id;

    // Viewers and outsiders cannot log; members cannot log for someone else, owners and admins can.
    mvc.perform(json(post(entries), team.viewer, entry(team.task, 30, null)))
        .andExpect(status().isForbidden());
    mvc.perform(json(post(entries), team.outsider, entry(team.task, 30, null)))
        .andExpect(status().isForbidden());
    mvc.perform(json(post(entries), team.member, entry(team.task, 30, team.admin.getId())))
        .andExpect(status().isForbidden());
    mvc.perform(json(post(entries), team.admin, entry(team.task, 30, team.member.getId())))
        .andExpect(status().isCreated());
    mvc.perform(json(post(entries), team.owner, entry(team.task, 30, team.viewer.getId())))
        .andExpect(status().isBadRequest());

    // Only the person or an owner/admin may change an entry.
    mvc.perform(json(put(path), team.viewer, correction(60)).header("If-Match", "\"0\""))
        .andExpect(status().isForbidden());
    User colleague = user();
    member(team.project, colleague, "Member");
    mvc.perform(json(put(path), colleague, correction(60)).header("If-Match", "\"0\""))
        .andExpect(status().isForbidden());
    mvc.perform(json(put(path), team.member, correction(60)))
        .andExpect(status().isPreconditionRequired());
    mvc.perform(json(put(path), team.member, correction(60)).header("If-Match", "\"0\""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.minutes").value(60))
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(json(put(path), team.admin, correction(45)).header("If-Match", "\"1\""))
        .andExpect(status().isOk());
    mvc.perform(json(put(path), team.owner, correction(40)).header("If-Match", "\"1\""))
        .andExpect(status().isPreconditionFailed());

    // Readers see the entries, outsiders do not.
    mvc.perform(get(entries).param("taskId", team.task.getId().toString()).with(as(team.viewer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2));
    mvc.perform(get(entries).with(as(team.outsider))).andExpect(status().isForbidden());

    team.archive(true);
    mvc.perform(delete(path).with(as(team.owner)).with(csrf()).header("If-Match", "\"2\""))
        .andExpect(status().isConflict());
    team.archive(false);
    mvc.perform(delete(path).with(as(team.owner)).with(csrf()).header("If-Match", "\"2\""))
        .andExpect(status().isNoContent());
  }

  @Test
  void validationRejectsImplausibleEntries() throws Exception {
    Team team = new Team();
    String entries = team.base() + "/time-entries";
    mvc.perform(json(post(entries), team.member, entry(team.task, 0, null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    mvc.perform(json(post(entries), team.member, entry(team.task, 1441, null)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            json(
                post(entries),
                team.member,
                "{\"taskId\":"
                    + team.task.getId()
                    + ",\"minutes\":30,\"spentOn\":\""
                    + LocalDate.now().plusDays(5)
                    + "\"}"))
        .andExpect(status().isBadRequest());
    Team other = new Team();
    mvc.perform(json(post(entries), team.member, entry(other.task, 30, null)))
        .andExpect(status().isBadRequest());
    mvc.perform(get(entries).param("size", "500").with(as(team.member)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
  }

  @Test
  void theTimerRunsOncePerPersonAndStopsIntoWholeMinutes() throws Exception {
    Team team = new Team();
    String timer = team.base() + "/timer";
    mvc.perform(get(timer).with(as(team.member))).andExpect(status().isNoContent());

    String start = "{\"taskId\":" + team.task.getId() + ",\"note\":\"pairing\"}";
    mvc.perform(json(post(timer), team.member, start))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.running").value(true))
        .andExpect(jsonPath("$.minutes").doesNotExist());
    mvc.perform(json(post(timer), team.member, start))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail")
                .value("A timer is already running. Stop it before starting another."));
    Team elsewhere = new Team();
    member(elsewhere.project, team.member, "Member");
    mvc.perform(
            json(
                post(elsewhere.base() + "/timer"),
                team.member,
                "{\"taskId\":" + elsewhere.task.getId() + "}"))
        .andExpect(status().isConflict());
    mvc.perform(json(post(timer), team.viewer, start)).andExpect(status().isForbidden());

    // Pretend it started 61.5 minutes ago: rounded up to 62.
    TimeEntry running = entries.findRunning(team.member.getId()).orElseThrow();
    running.setStartedAt(Instant.now().minus(61 * 60 + 30, ChronoUnit.SECONDS));
    running = entries.saveAndFlush(running);
    mvc.perform(get(timer).with(as(team.member)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.note").value("pairing"));
    mvc.perform(post(timer + "/stop").with(as(team.member)).with(csrf()))
        .andExpect(status().isPreconditionRequired());
    mvc.perform(
            post(timer + "/stop")
                .with(as(team.member))
                .with(csrf())
                .header("If-Match", "\"" + running.getVersion() + "\""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.running").value(false))
        .andExpect(jsonPath("$.minutes").value(62));
    mvc.perform(get(timer).with(as(team.member))).andExpect(status().isNoContent());
    mvc.perform(
            post(timer + "/stop").with(as(team.member)).with(csrf()).header("If-Match", "\"1\""))
        .andExpect(status().isNotFound());
  }

  @Test
  void estimatesNeedTheTaskVersionAndSurviveOrdinaryTaskUpdates() throws Exception {
    Team team = new Team();
    Task task = task(team.project, team.sprint, team.member, 2, null);
    String path = team.base() + "/tasks/" + task.getId() + "/time-estimate";
    mvc.perform(json(put(path), team.member, "{\"estimateMinutes\":240}"))
        .andExpect(status().isPreconditionRequired());
    mvc.perform(
            json(put(path), team.viewer, "{\"estimateMinutes\":240}").header("If-Match", "\"0\""))
        .andExpect(status().isForbidden());
    mvc.perform(
            json(put(path), team.member, "{\"estimateMinutes\":-1}").header("If-Match", "\"0\""))
        .andExpect(status().isBadRequest());
    mvc.perform(
            json(put(path), team.member, "{\"estimateMinutes\":240}").header("If-Match", "\"0\""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estimateMinutes").value(240))
        .andExpect(jsonPath("$.version").value(1));

    // An update through the task endpoint has no estimate field and leaves it alone.
    mvc.perform(
            json(
                    put("/api/tasks/" + task.getId()),
                    team.member,
                    "{\"title\":\"Renamed\",\"status\":\"To Do\",\"projectId\":"
                        + team.project.getId()
                        + ",\"sprintId\":"
                        + team.sprint.getId()
                        + ",\"storyPoints\":2,"
                        + "\"estimateMinutes\":5}")
                .header("If-Match", "\"1\""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estimateMinutes").value(240));

    mvc.perform(
            json(put(path), team.member, "{\"estimateMinutes\":null}").header("If-Match", "\"2\""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estimateMinutes").doesNotExist());
  }

  @Test
  void theCapacityViewComparesCommittedWorkWithAvailabilityAndDaysOff() throws Exception {
    Team team = new Team(); // the member already holds a 3-point, 120-minute task
    task(team.project, team.sprint, team.member, 5, 2_100);
    task(team.project, team.sprint, team.admin, 8, null);
    task(team.project, team.sprint, null, 2, 60);
    // Wednesday 4 March is a working day off; Saturday 7 March and 1 April cost nothing.
    mvc.perform(
            json(
                put(capacityPath(team, team.member)),
                team.member,
                "{\"minutesPerDay\":480,\"availabilityPercent\":50,\"pointCapacity\":10,"
                    + "\"daysOff\":[\"2026-03-04\",\"2026-03-07\",\"2026-04-01\",\"2026-03-04\"]}"))
        .andExpect(status().isOk());
    logged(team, team.member, 75);

    String row = memberRow(team.member);
    mvc.perform(
            get(team.base() + "/sprints/" + team.sprint.getId() + "/capacity")
                .with(as(team.viewer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workingDays").value(10))
        .andExpect(jsonPath(row + ".daysOff.length()").value(3))
        .andExpect(jsonPath(row + ".availableDays").value(9))
        // 9 days x 480 minutes x 50 %
        .andExpect(jsonPath(row + ".availableMinutes").value(2160))
        // 10 points x 9/10 days x 50 %, rounded down
        .andExpect(jsonPath(row + ".effectivePoints").value(4))
        .andExpect(jsonPath(row + ".committedMinutes").value(2220))
        .andExpect(jsonPath(row + ".committedPoints").value(8))
        .andExpect(jsonPath(row + ".loggedMinutes").value(75))
        .andExpect(jsonPath(row + ".loadPercent").value(103))
        .andExpect(jsonPath(row + ".overbooked").value(true))
        .andExpect(jsonPath(row + ".pointsOverbooked").value(true))
        .andExpect(jsonPath(memberRow(team.admin) + ".configured").value(false))
        .andExpect(jsonPath("$.members.length()").value(3))
        .andExpect(jsonPath("$.team.unassignedMinutes").value(60))
        .andExpect(jsonPath("$.team.committedMinutes").value(2280))
        .andExpect(jsonPath("$.team.overbooked").value(true))
        .andExpect(
            jsonPath("$.warnings[?(@.code == 'MEMBER_OVERBOOKED')].userId")
                .value(team.member.getId().intValue()))
        .andExpect(
            jsonPath("$.warnings[?(@.code == 'CAPACITY_NOT_SET')].userId")
                .value(team.admin.getId().intValue()))
        .andExpect(jsonPath("$.warnings[?(@.code == 'UNASSIGNED_WORK')]").isNotEmpty());

    // The same numbers feed the C1 optimizer without translation.
    OptimizerCapacity hours =
        capacityService.optimizerCapacity(
            team.project.getId(), team.sprint.getId(), CapacityUnit.HOURS);
    String memberKey = OptimizerCapacity.memberKey(team.member.getId());
    assertThat(hours.memberCapacities())
        .containsEntry(memberKey, 36)
        .containsEntry(OptimizerCapacity.memberKey(team.admin.getId()), 0)
        .containsEntry(OptimizerCapacity.memberKey(team.owner.getId()), 0)
        .doesNotContainKey(OptimizerCapacity.memberKey(team.viewer.getId()));
    OptimizerCapacity points =
        capacityService.optimizerCapacity(
            team.project.getId(), team.sprint.getId(), CapacityUnit.POINTS);
    assertThat(points.totalCapacity()).isEqualTo(4);
    PlanningProblem problem =
        new PlanningProblem(
            List.of(
                new CandidateStory("fits", 10, 3, Map.of(memberKey, 3), List.of(), Lock.NONE),
                new CandidateStory("too-big", 50, 3, Map.of(memberKey, 5), List.of(), Lock.NONE)),
            java.util.Set.of(),
            points.totalCapacity(),
            points.memberCapacities());
    assertThat(new GreedySprintPlanSolver().solve(problem).selectedStoryIds())
        .containsExactly("fits");
  }

  @Test
  void theCapacityViewCostsTheSameNumberOfQueriesForAnyTeamSize() throws Exception {
    Team small = new Team();
    long withThree =
        statementsFor(() -> mvc.perform(get(capacityUrl(small)).with(as(small.owner))));

    Team large = new Team();
    for (int i = 0; i < 6; i++) {
      User extra = user();
      member(large.project, extra, "Member");
      task(large.project, large.sprint, extra, 1, 30);
      mvc.perform(json(put(capacityPath(large, extra)), extra, capacity(480, 100, 5)))
          .andExpect(status().isOk());
      logged(large, extra, 15);
    }
    long withNine = statementsFor(() -> mvc.perform(get(capacityUrl(large)).with(as(large.owner))));
    assertThat(withNine).isEqualTo(withThree);
    // session user + access row + sprint + capacities + members + committed + logged
    assertThat(withNine).isLessThanOrEqualTo(7);

    long reportSmall =
        statementsFor(() -> mvc.perform(get(small.base() + "/time-report").with(as(small.owner))));
    long reportLarge =
        statementsFor(() -> mvc.perform(get(large.base() + "/time-report").with(as(large.owner))));
    assertThat(reportLarge).isEqualTo(reportSmall);

    long entriesSmall =
        statementsFor(() -> mvc.perform(get(small.base() + "/time-entries").with(as(small.owner))));
    long entriesLarge =
        statementsFor(() -> mvc.perform(get(large.base() + "/time-entries").with(as(large.owner))));
    assertThat(entriesLarge).isEqualTo(entriesSmall);
  }

  @Test
  void entriesArePagedNewestFirstWithAKeysetCursor() throws Exception {
    Team team = new Team();
    for (int i = 1; i <= 5; i++) logged(team, team.member, i);
    MvcResult first =
        mvc.perform(get(team.base() + "/time-entries").param("size", "2").with(as(team.member)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].minutes").value(5))
            .andExpect(jsonPath("$.items[1].minutes").value(4))
            .andReturn();
    Number next = JsonPath.read(first.getResponse().getContentAsString(), "$.nextBefore");
    mvc.perform(
            get(team.base() + "/time-entries")
                .param("size", "3")
                .param("before", next.toString())
                .with(as(team.member)))
        .andExpect(jsonPath("$.items[0].minutes").value(3))
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.nextBefore").doesNotExist());
  }

  @Test
  void theReportComparesEstimatesWithLoggedTimeAndExportsSafeCsv() throws Exception {
    Team team = new Team(); // 120-minute estimate
    Task formula = task(team.project, team.sprint, team.member, 1, null);
    formula.setTitle("=HYPERLINK(\"x\")");
    tasks.save(formula);
    logged(team, team.member, 150);
    entries.save(entry(formula, team.member, 30));

    mvc.perform(
            get(team.base() + "/time-report")
                .param("sprintId", team.sprint.getId().toString())
                .with(as(team.viewer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estimatedMinutes").value(120))
        .andExpect(jsonPath("$.loggedMinutes").value(180))
        .andExpect(jsonPath("$.loggedOnEstimatedMinutes").value(150))
        .andExpect(jsonPath("$.varianceMinutes").value(30))
        .andExpect(jsonPath("$.variancePercent").value(25))
        .andExpect(jsonPath("$.unestimatedTasks").value(1))
        .andExpect(jsonPath("$.rows[0].varianceMinutes").value(30));
    mvc.perform(get(team.base() + "/time-report.csv").with(as(team.member)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/csv"))
        .andExpect(
            header()
                .string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("\"'=HYPERLINK(\"\"x\"\")\"")));
    mvc.perform(get(team.base() + "/time-report.csv").with(as(team.outsider)))
        .andExpect(status().isForbidden());
    mvc.perform(
            get(team.base() + "/time-report").param("sprintId", "999999999").with(as(team.member)))
        .andExpect(status().isNotFound());
  }

  @Test
  void deletedAccountsLeaveAnonymousHoursAndNoRunningTimer() throws Exception {
    Team team = new Team();
    logged(team, team.member, 45);
    TimeEntry timer = new TimeEntry();
    timer.setProjectId(team.project.getId());
    timer.setTaskId(team.task.getId());
    timer.setUserId(team.member.getId());
    timer.setStartedAt(Instant.now());
    timer.setSpentOn(LocalDate.now());
    timer.setCreatedAt(Instant.now());
    entries.save(timer);

    new org.springframework.transaction.support.TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              assertThat(entries.deleteRunningFor(team.member.getId())).isEqualTo(1);
              assertThat(entries.anonymize(team.member.getId())).isEqualTo(1);
            });
    mvc.perform(get(capacityUrl(team)).with(as(team.owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.team.loggedMinutes").value(45));
    mvc.perform(get(team.base() + "/time-report").with(as(team.owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loggedMinutes").value(45));
    assertThat(entries.findAll())
        .filteredOn(e -> team.task.getId().equals(e.getTaskId()))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getUserId()).isNull();
              assertThat(e.getMinutes()).isEqualTo(45);
            });
  }

  @Autowired private org.springframework.transaction.PlatformTransactionManager transactions;

  @ParameterizedTest
  @ValueSource(strings = {"Owner", "Admin", "Member", "Viewer", "Outsider", "Archived", "Trashed"})
  void allEndpointsRespectRolesArchiveAndProjectTrash(String role) throws Exception {
    Team team = new Team();
    User caller =
        switch (role) {
          case "Admin" -> team.admin;
          case "Member" -> team.member;
          case "Viewer" -> team.viewer;
          case "Outsider" -> team.outsider;
          default -> team.owner;
        };
    TimeEntry logged = entries.saveAndFlush(entry(team.task, team.member, 15));
    if (role.equals("Archived")) team.archive(true);
    if (role.equals("Trashed")) {
      var project = projects.findById(team.project.getId()).orElseThrow();
      project.setTrashedAt(Instant.now());
      projects.save(project);
    }
    int read = role.equals("Outsider") ? 403 : role.equals("Trashed") ? 404 : 200;
    for (String path :
        List.of(
            "/time-entries",
            "/time-report",
            "/time-report.csv",
            "/sprints/" + team.sprint.getId() + "/capacity"))
      mvc.perform(get(team.base() + path).with(as(caller))).andExpect(status().is(read));
    mvc.perform(get(team.base() + "/timer").with(as(caller)))
        .andExpect(status().is(read == 200 ? 204 : read));
    int write =
        role.equals("Trashed")
            ? 404
            : role.equals("Archived")
                ? 409
                : List.of("Owner", "Admin", "Member").contains(role) ? 200 : 403;
    mvc.perform(json(post(team.base() + "/time-entries"), caller, entry(team.task, 30, null)))
        .andExpect(status().is(write == 200 ? 201 : write));
    mvc.perform(
            json(put(team.base() + "/time-entries/" + logged.getId()), caller, correction(20))
                .header("If-Match", "\"0\""))
        .andExpect(status().is(write));
    mvc.perform(
            delete(team.base() + "/time-entries/" + logged.getId())
                .with(as(caller))
                .with(csrf())
                .header("If-Match", "\"1\""))
        .andExpect(status().is(write == 200 ? 204 : write));
    mvc.perform(
            json(
                    put(team.base() + "/tasks/" + team.task.getId() + "/time-estimate"),
                    caller,
                    "{\"estimateMinutes\":180}")
                .header("If-Match", "\"0\""))
        .andExpect(status().is(write));
    mvc.perform(json(put(capacityPath(team, caller)), caller, capacity(480, 100, null)))
        .andExpect(status().is(write));
    mvc.perform(
            delete(capacityPath(team, caller))
                .with(as(caller))
                .with(csrf())
                .header("If-Match", "\"0\""))
        .andExpect(status().is(write == 200 ? 204 : write));
    mvc.perform(
            json(post(team.base() + "/timer"), caller, "{\"taskId\":" + team.task.getId() + "}"))
        .andExpect(status().is(write == 200 ? 201 : write));
    mvc.perform(
            post(team.base() + "/timer/stop")
                .with(as(caller))
                .with(csrf())
                .header("If-Match", "\"0\""))
        .andExpect(status().is(write));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Removed", "Viewer", "Archived", "Trashed"})
  void losingWriteAccessCannotTrapARunningTimerAcrossProjects(String change) throws Exception {
    Team old = new Team();
    Team next = new Team();
    member(next.project, old.member, "Member");
    logged(old, old.member, 45);
    mvc.perform(
            json(post(old.base() + "/timer"), old.member, "{\"taskId\":" + old.task.getId() + "}"))
        .andExpect(status().isCreated());
    switch (change) {
      case "Removed" ->
          mvc.perform(
                  delete(old.base() + "/members/" + old.member.getEmail())
                      .with(as(old.owner))
                      .with(csrf()))
              .andExpect(status().isNoContent());
      case "Viewer" ->
          mvc.perform(
                  json(
                      post(old.base() + "/members"),
                      old.owner,
                      "{\"email\":\"" + old.member.getEmail() + "\",\"role\":\"Viewer\"}"))
              .andExpect(status().isOk());
      case "Archived" -> old.archive(true);
      default -> {
        var project = projects.findById(old.project.getId()).orElseThrow();
        project.setTrashedAt(Instant.now());
        projects.save(project);
      }
    }
    mvc.perform(
            json(
                post(next.base() + "/timer"), old.member, "{\"taskId\":" + next.task.getId() + "}"))
        .andExpect(status().isCreated());
    assertThat(entries.findRunning(old.member.getId()))
        .hasValueSatisfying(
            timer -> assertThat(timer.getProjectId()).isEqualTo(next.project.getId()));
    assertThat(entries.findAll())
        .filteredOn(entry -> entry.getTaskId().equals(old.task.getId()))
        .singleElement()
        .satisfies(entry -> assertThat(entry.getMinutes()).isEqualTo(45));
  }

  // ---- fixtures

  private Sprint sprint(Project project) {
    Sprint sprint = new Sprint("Sprint " + NUMBERS.incrementAndGet(), null, "Planned", 0);
    sprint.setProjectId(project.getId());
    sprint.setStartDate(START);
    sprint.setEndDate(END);
    return sprints.save(sprint);
  }

  private Task task(Project project, Sprint sprint, User assignee, int points, Integer estimate) {
    Task task = new Task("Task " + NUMBERS.get(), null, "To Do");
    task.setProjectId(project.getId());
    task.setSprintId(sprint.getId());
    task.setAssigneeEmail(assignee == null ? null : assignee.getEmail());
    task.setStoryPoints(points);
    task.setEstimateMinutes(estimate);
    task.setNumber(NUMBERS.incrementAndGet());
    return tasks.save(task);
  }

  private void logged(Team team, User user, int minutes) {
    entries.save(entry(team.task, user, minutes));
  }

  private static TimeEntry entry(Task task, User user, int minutes) {
    TimeEntry entry = new TimeEntry();
    entry.setProjectId(task.getProjectId());
    entry.setTaskId(task.getId());
    entry.setUserId(user.getId());
    entry.setMinutes(minutes);
    entry.setSpentOn(START);
    entry.setCreatedAt(Instant.now());
    return entry;
  }

  private MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder request, User user, String body) {
    return request
        .with(as(user))
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private static String capacity(int minutes, int availability, Integer points) {
    return "{\"minutesPerDay\":"
        + minutes
        + ",\"availabilityPercent\":"
        + availability
        + ",\"pointCapacity\":"
        + points
        + ",\"daysOff\":[]}";
  }

  private static String entry(Task task, int minutes, Long userId) {
    return "{\"taskId\":"
        + task.getId()
        + ",\"minutes\":"
        + minutes
        + ",\"spentOn\":\""
        + START
        + "\",\"note\":\"Review\""
        + (userId == null ? "" : ",\"userId\":" + userId)
        + "}";
  }

  private static String correction(int minutes) {
    return "{\"minutes\":" + minutes + ",\"spentOn\":\"" + START + "\",\"note\":null}";
  }

  private static String capacityPath(Team team, User user) {
    return team.base() + "/sprints/" + team.sprint.getId() + "/capacity/" + user.getId();
  }

  private static String capacityUrl(Team team) {
    return team.base() + "/sprints/" + team.sprint.getId() + "/capacity";
  }

  private static String memberRow(User user) {
    return "$.members[?(@.userId == " + user.getId() + ")]";
  }

  private String memberVersion(Team team, User user) throws Exception {
    String body =
        mvc.perform(get(capacityUrl(team)).with(as(team.owner)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<Number> versions = JsonPath.read(body, memberRow(user) + ".version");
    return "\"" + versions.get(0) + "\"";
  }

  private static long id(MvcResult result) throws Exception {
    Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    return id.longValue();
  }
}
