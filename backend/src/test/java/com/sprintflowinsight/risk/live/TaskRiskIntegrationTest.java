package com.sprintflowinsight.risk.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprintflowinsight.support.ApiIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TaskRiskIntegrationTest extends ApiIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired TaskRiskService service;
  @Autowired RiskFeatureReader reader;
  @Autowired RiskHistory history;

  @Test
  void observesOnceAndLabelsTheStatusAtSprintEndBeforePersistingTheCalibratedModel()
      throws Exception {
    var owner = user();
    long project = project(owner, null).getId();
    var start = Instant.now().minus(28, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    long sprint = sprint(project, start.plus(5, ChronoUnit.DAYS));
    long first = task(project, sprint, 1, start);
    long second = task(project, sprint, 2, start);
    jdbc.update(
        "update task set is_blocked=true,assignee_email=? where id=?", owner.getEmail(), first);
    jdbc.update("update task set is_blocked=false where id=?", first);
    jdbc.update("update task set is_blocked=true where id=?", first);
    jdbc.update(
        "insert into task_dependency(project_id,blocker_task_id,blocked_task_id,created_at)"
            + " values(?,?,?,?)",
        project,
        first,
        second,
        Timestamp.from(start));
    jdbc.update(
        "insert into task_comment(project_id,task_id,author_email,message,created_at)"
            + " values(?,?,?,'Fixture',?)",
        project,
        second,
        owner.getEmail(),
        Timestamp.from(start));
    var sampleAt = start.plus(1, ChronoUnit.DAYS);
    var rows = reader.read(project, sprint, sampleAt, 4);
    assertThat(rows).hasSize(2);
    assertThat(rows.getFirst().features().timesBlocked()).isEqualTo(2);
    assertThat(rows.getFirst().features().reassignments()).isEqualTo(1);
    assertThat(rows.get(1).features().dependencyDepth()).isEqualTo(1);
    assertThat(rows.get(1).features().commentsLast7Days()).isEqualTo(1);
    assertThat(rows.get(1).features().columnWip()).isEqualTo(2);
    service.train(project, sampleAt);
    service.train(project, sampleAt.plus(2, ChronoUnit.HOURS));
    assertThat(history.trainingExamples(project)).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from task_risk_observation where project_id=?",
                Integer.class,
                project))
        .isEqualTo(2);

    var ended = start.plus(4, ChronoUnit.DAYS);
    jdbc.update(
        "update sprint set status='Completed',completed_at=? where id=?",
        Timestamp.from(ended),
        sprint);
    statusHistory(project, sprint, first, "Done", ended.minus(1, ChronoUnit.HOURS));
    statusHistory(project, sprint, first, "To Do", ended.plus(1, ChronoUnit.HOURS));
    statusHistory(project, sprint, second, "Done", ended.plus(2, ChronoUnit.HOURS));
    service.train(project, ended.plus(1, ChronoUnit.DAYS));
    var examples = history.trainingExamples(project);
    assertThat(examples).hasSize(2);
    assertThat(examples.getFirst().observedAt()).isEqualTo(sampleAt);
    assertThat(examples.getFirst().late()).isFalse();
    assertThat(examples.get(1).late()).isTrue();
    assertThat(history.model(project).orElseThrow().exampleCount()).isEqualTo(2);

    long active = sprint(project, Instant.now().plus(5, ChronoUnit.DAYS));
    long openTask = task(project, active, 3, Instant.now());
    String path = "/api/projects/" + project + "/task-risk";
    mvc.perform(get(path).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sprintId").value(active))
        .andExpect(jsonPath("$.exampleCount").value(2))
        .andExpect(jsonPath("$.tasks[0].taskId").value(openTask))
        .andExpect(jsonPath("$.tasks[0].prediction.source").value("HEURISTIC"))
        .andExpect(jsonPath("$.tasks[0].prediction.probability").isNumber());
  }

  @Test
  void requiresCurrentProjectMembershipForEveryRead() throws Exception {
    var owner = user();
    var project = project(owner, null);
    long active = sprint(project.getId(), Instant.now().plus(3, ChronoUnit.DAYS));
    task(project.getId(), active, 1, Instant.now());
    String path = "/api/projects/" + project.getId() + "/task-risk";
    for (String role : List.of("Owner", "Admin", "Member", "Viewer")) {
      var caller = role.equals("Owner") ? owner : user();
      if (!role.equals("Owner")) member(project, caller, role);
      mvc.perform(get(path).with(as(caller)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.tasks.length()").value(1));
    }
    mvc.perform(get(path).with(as(user()))).andExpect(status().isForbidden());
    jdbc.update("update projects set trashed_at=now() where id=?", project.getId());
    mvc.perform(get(path).with(as(owner))).andExpect(status().isNotFound());
  }

  private long sprint(long project, Instant end) {
    var date = LocalDate.ofInstant(end, ZoneOffset.UTC);
    return jdbc.queryForObject(
        "insert into sprint(project_id,name,status,start_date,end_date,"
            + "total_story_points,version) values(?,'Risk sprint','Active',?,?,0,0) returning id",
        Long.class,
        project,
        date.minusDays(6),
        date);
  }

  private long task(long project, long sprint, int number, Instant when) {
    long task =
        jdbc.queryForObject(
            "insert into"
                + " task(project_id,sprint_id,title,status,is_blocked,number,story_points,version,created_at)"
                + " values(?,?,'Risk task','To Do',false,?,3,0,?) returning id",
            Long.class,
            project,
            sprint,
            number,
            Timestamp.from(when));
    statusHistory(project, sprint, task, "To Do", when);
    return task;
  }

  private void statusHistory(long project, long sprint, long task, String status, Instant time) {
    jdbc.update(
        "insert into task_status_history(project_id,sprint_id,task_id,status,changed_at)"
            + " values(?,?,?,?,?)",
        project,
        sprint,
        task,
        status,
        Timestamp.from(time));
  }
}
