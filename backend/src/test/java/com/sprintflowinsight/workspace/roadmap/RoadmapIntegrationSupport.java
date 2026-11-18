package com.sprintflowinsight.workspace.roadmap;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.sprintflowinsight.support.ApiIntegrationTest;
import com.sprintflowinsight.workspace.project.Project;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

abstract class RoadmapIntegrationSupport extends ApiIntegrationTest {
  @Autowired protected JdbcTemplate jdbc;
  private final JsonMapper json = JsonMapper.builder().build();

  protected String path(Project project) {
    return "/api/projects/" + project.getId();
  }

  protected String json(Object value) {
    return json.writeValueAsString(value);
  }

  protected MockHttpServletRequestBuilder body(
      MockHttpServletRequestBuilder request, Object value) {
    return request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(value));
  }

  protected long task(Project project, int number, String status) {
    return jdbc.queryForObject(
        "insert into"
            + " task(title,status,is_blocked,project_id,story_points,number,created_at,version)"
            + " values (?, ?, false, ?, 1, ?, now(),0) returning id",
        Long.class,
        "Task " + number,
        status,
        project.getId(),
        number);
  }

  protected long epic(Project project, String title) {
    return jdbc.queryForObject(
        "insert into epic(project_id,title,created_at) values (?,?,now()) returning id",
        Long.class,
        project.getId(),
        title);
  }

  protected long story(Project project, Long epic) {
    return jdbc.queryForObject(
        "insert into"
            + " user_stories(project_id,title,acceptance_criteria,story_points,priority_id,epic_id,version)"
            + " values (?,'Story','Shipped',3,1,?,0) returning id",
        Long.class,
        project.getId(),
        epic);
  }

  protected long dependency(Project project, long from, long to) {
    return jdbc.queryForObject(
        "insert into task_dependency(project_id,blocker_task_id,blocked_task_id,created_at) values"
            + " (?,?,?,now()) returning id",
        Long.class,
        project.getId(),
        from,
        to);
  }

  protected void estimate(Project project, long task, int days) {
    jdbc.update(
        "insert into"
            + " task_estimate(task_id,project_id,optimistic_days,most_likely_days,pessimistic_days,updated_at)"
            + " values (?,?,?,?,?,now())",
        task,
        project.getId(),
        days,
        days,
        days);
  }

  protected Map<String, Object> epicBody() {
    return Map.of(
        "title",
        "Release",
        "color",
        "#AB12CD",
        "startDate",
        "2026-01-01",
        "targetDate",
        "2030-12-31");
  }
}
