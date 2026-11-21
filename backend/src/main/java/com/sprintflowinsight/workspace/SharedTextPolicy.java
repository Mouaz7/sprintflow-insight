package com.sprintflowinsight.workspace;

import com.sprintflowinsight.common.error.ApiException;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Keep ordinary metadata writes from overwriting the text managed by an open CRDT room. */
@Component
public class SharedTextPolicy {
  private final JdbcTemplate jdbc;

  SharedTextPolicy(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void task(long projectId, long id, String before, String after) {
    reject("task_id", projectId, id, before, after);
  }

  public void retrospective(long projectId, long id, String before, String after) {
    reject("retrospective_id", projectId, id, before, after);
  }

  private void reject(String column, long projectId, long id, String before, String after) {
    if (Objects.equals(
        Objects.requireNonNullElse(before, ""), Objects.requireNonNullElse(after, ""))) return;
    if (Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from crdt_doc where project_id=? and " + column + "=?)",
            Boolean.class,
            projectId,
            id))) throw ApiException.conflict("Use the shared editor to change this text.");
  }
}
