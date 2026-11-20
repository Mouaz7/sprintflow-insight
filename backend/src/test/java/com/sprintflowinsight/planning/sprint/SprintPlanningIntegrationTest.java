package com.sprintflowinsight.planning.sprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.support.ApiIntegrationTest;
import com.sprintflowinsight.workspace.project.Project;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class SprintPlanningIntegrationTest extends ApiIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @Test
  void proposalsRespectExistingWorkDependenciesLocksAndMemberCapacity() throws Exception {
    var owner = user();
    var peer = user();
    var p = project(owner, peer);
    long sprint = sprint(p);
    capacity(p, sprint, owner, 4);
    capacity(p, sprint, peer, 5);
    long prerequisite = story(p, "Prerequisite", 3, 2);
    long value = story(p, "Value", 2, 5);
    long expensive = story(p, "Would exceed owner capacity", 5, 5);
    long taskA = task(p, owner, 1, prerequisite, null, 3);
    long taskB = task(p, peer, 2, value, null, 2);
    task(p, owner, 3, expensive, null, 5);
    task(p, owner, 4, null, sprint, 1);
    jdbc.update(
        "insert into task_dependency(project_id,blocker_task_id,blocked_task_id,created_at)"
            + " values(?,?,?,now())",
        p.getId(),
        taskA,
        taskB);

    String endpoint = "/api/projects/" + p.getId() + "/sprint-plan";
    mvc.perform(get(endpoint + "/options").param("sprintId", "" + sprint).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reservedEffort").value(1))
        .andExpect(jsonPath("$.configuredCapacity").value(9));
    var body =
        Map.of(
            "sprintId",
            sprint,
            "unit",
            "POINTS",
            "memberCapacities",
            Map.of(),
            "lockedIn",
            List.of(value),
            "lockedOut",
            List.of());
    mvc.perform(
            post(endpoint + "/optimize")
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.plan.objective").value(7))
        .andExpect(jsonPath("$.plan.selectedStoryIds", contains("" + prerequisite, "" + value)))
        .andExpect(jsonPath("$.plan.relativeGap").value(0.0));

    var whatIf =
        Map.of(
            "sprintId",
            sprint,
            "unit",
            "POINTS",
            "memberCapacities",
            Map.of(owner.getId(), 9),
            "lockedIn",
            List.of(),
            "lockedOut",
            List.of());
    mvc.perform(
            post(endpoint + "/optimize")
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(whatIf)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.plan.objective").value(12));

    var conflict =
        Map.of(
            "sprintId",
            sprint,
            "unit",
            "POINTS",
            "memberCapacities",
            Map.of(),
            "lockedIn",
            List.of(value),
            "lockedOut",
            List.of(prerequisite));
    mvc.perform(
            post(endpoint + "/optimize")
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(conflict)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.plan.status").value("INFEASIBLE"))
        .andExpect(jsonPath("$.plan.explanation.conflicts[0].kind").value("LOCKED_IN_BLOCKED"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from user_stories where project_id=? and sprint_id is not null",
                Integer.class,
                p.getId()))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select point_capacity from sprint_capacity where sprint_id=? and user_id=?",
                Integer.class,
                sprint,
                owner.getId()))
        .isEqualTo(4);
  }

  @Test
  void onlyCurrentProjectMembersCanReadOrProposeAndCSRFIsRequired() throws Exception {
    var owner = user();
    var p = project(owner, null);
    long sprint = sprint(p);
    String path = "/api/projects/" + p.getId() + "/sprint-plan";
    String body =
        json.writeValueAsString(
            Map.of(
                "sprintId",
                sprint,
                "unit",
                "POINTS",
                "lockedIn",
                List.of(),
                "lockedOut",
                List.of(),
                "memberCapacities",
                Map.of()));
    for (String role : List.of("Owner", "Admin", "Member", "Viewer", "Outsider")) {
      User caller = "Owner".equals(role) ? owner : user();
      if (!List.of("Owner", "Outsider").contains(role)) member(p, caller, role);
      int expected = "Outsider".equals(role) ? 403 : 200;
      mvc.perform(get(path + "/options").param("sprintId", "" + sprint).with(as(caller)))
          .andExpect(status().is(expected));
      mvc.perform(
              post(path + "/optimize")
                  .with(as(caller))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().is(expected));
    }
    mvc.perform(
            post(path + "/optimize")
                .with(as(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    long foreignSprint = sprint(project(user(), null));
    mvc.perform(get(path + "/options").param("sprintId", "" + foreignSprint).with(as(owner)))
        .andExpect(status().isNotFound());
    jdbc.update("update projects set archived_at=now() where id=?", p.getId());
    mvc.perform(
            post(path + "/optimize")
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
    jdbc.update("update projects set trashed_at=now() where id=?", p.getId());
    mvc.perform(get(path + "/options").param("sprintId", "" + sprint).with(as(owner)))
        .andExpect(status().isNotFound());
  }

  private long sprint(Project p) {
    return jdbc.queryForObject(
        "insert into sprint(project_id,name,status,start_date,end_date,total_story_points,version)"
            + " values(?,'Plan','Planned',?,?,0,0) returning id",
        Long.class,
        p.getId(),
        LocalDate.of(2026, 11, 23),
        LocalDate.of(2026, 12, 4));
  }

  private void capacity(Project p, long sprint, User user, int points) {
    jdbc.update(
        "insert into"
            + " sprint_capacity(project_id,sprint_id,user_id,minutes_per_day,availability_percent,point_capacity,days_off,updated_at,version)"
            + " values(?,?,?,480,100,?,'{}',now(),0)",
        p.getId(),
        sprint,
        user.getId(),
        points);
  }

  private long story(Project p, String title, int points, int value) {
    return jdbc.queryForObject(
        "insert into"
            + " user_stories(project_id,title,acceptance_criteria,story_points,priority_id,business_value,version)"
            + " values(?,?,'Criteria',?,2,?,0) returning id",
        Long.class,
        p.getId(),
        title,
        points,
        value);
  }

  private long task(Project p, User assignee, int number, Long story, Long sprint, int points) {
    return jdbc.queryForObject(
        "insert into"
            + " task(project_id,title,status,is_blocked,number,story_id,sprint_id,assignee_email,story_points,version)"
            + " values(?,'Task','To Do',false,?,?,?,?,?,0) returning id",
        Long.class,
        p.getId(),
        number,
        story,
        sprint,
        assignee.getEmail(),
        points);
  }
}
