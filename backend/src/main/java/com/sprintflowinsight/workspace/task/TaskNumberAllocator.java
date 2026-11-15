package com.sprintflowinsight.workspace.task;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hands out the next task number of a project. A single upsert on {@code project_task_counter}
 * takes that row's lock until the surrounding transaction ends, so concurrent creators are
 * serialised per project and never receive the same number; the unique index {@code
 * task_project_number_unique} remains the final guard.
 */
@Component
public class TaskNumberAllocator {
  static final String NEXT =
      """
      insert into project_task_counter(project_id, last_number) values (:project, 1)
      on conflict (project_id) do update set last_number = project_task_counter.last_number + 1
      returning last_number, (select key from projects where id = :project) as project_key
      """;

  private final JdbcClient jdbc;

  public TaskNumberAllocator(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public record Allocation(String projectKey, int number) {}

  @Transactional
  public Allocation next(long projectId) {
    return jdbc.sql(NEXT)
        .param("project", projectId)
        .query(
            (row, index) -> new Allocation(row.getString("project_key"), row.getInt("last_number")))
        .single();
  }
}
