package com.sprintflowinsight.search;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprintflowinsight.support.ApiIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class TaskDuplicateIntegrationTest extends ApiIntegrationTest {
  @Autowired JdbcTemplate jdbc;

  @Test
  void matchesSimilarLiveTasksOnlyInTheAuthorizedProject() throws Exception {
    var owner = user();
    var project = project(owner, null);
    long other = project(user(), null).getId();
    long task = task(project.getId(), 1, "Syncing customer search indexes");
    task(project.getId(), 2, "Pin account menu on a mobile screen");
    long deleted = task(project.getId(), 3, "Sync customer search index");
    task(other, 1, "Sync customer search index");
    jdbc.update("update task set trashed_at=now() where id=?", deleted);
    String url = "/api/projects/" + project.getId() + "/task-duplicates";
    var query =
        post(url)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Sync customer search index\"}");
    mvc.perform(query.with(as(owner))).andExpect(status().isForbidden());
    for (String role : List.of("Owner", "Admin", "Member", "Viewer")) {
      var caller = role.equals("Owner") ? owner : user();
      if (!role.equals("Owner")) member(project, caller, role);
      mvc.perform(
              post(url)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"title\":\"Sync customer search index\"}")
                  .with(as(caller))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].taskId").value(task))
          .andExpect(jsonPath("$[0].key").value(project.getKey() + "-1"));
    }
    mvc.perform(
            post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Sync customer search index\"}")
                .with(as(user()))
                .with(csrf()))
        .andExpect(status().isForbidden());
  }

  private long task(long project, int number, String title) {
    return jdbc.queryForObject(
        "insert into task(project_id,title,status,is_blocked,number,story_points,version)"
            + " values(?,?,'To Do',false,?,0,0) returning id",
        Long.class,
        project,
        title,
        number);
  }
}
