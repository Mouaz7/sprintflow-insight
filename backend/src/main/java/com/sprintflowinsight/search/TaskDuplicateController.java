package com.sprintflowinsight.search;

import com.sprintflowinsight.workspace.project.ProjectAccessService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/task-duplicates")
class TaskDuplicateController {
  static final int MIN_TITLE_LENGTH = 4;
  static final double SCORE_THRESHOLD = 0.35;

  private final ProjectAccessService access;
  private final JdbcTemplate jdbc;

  TaskDuplicateController(ProjectAccessService access, JdbcTemplate jdbc) {
    this.access = access;
    this.jdbc = jdbc;
  }

  record Draft(@NotBlank @Size(max = 255) String title) {}

  record Match(long taskId, String key, String title, String status, double score) {}

  @PostMapping
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<Match> matches(
      @PathVariable long projectId, @Valid @RequestBody Draft draft, HttpSession session) {
    access.read(projectId, session);
    String title = draft.title().strip();
    if (title.length() < MIN_TITLE_LENGTH) return List.of();
    // Explicit per-transaction candidate threshold keeps the GIN prefilter broad and predictable.
    jdbc.queryForObject(
        "select set_config('pg_trgm.similarity_threshold','0.10',true)", String.class);
    return jdbc.query(
        """
with candidates as materialized (
  select t.id,p.key || '-' || t.number as key,t.title,t.status,task_duplicate_score(?,t.title) as score
  from task t join projects p on p.id=t.project_id
  where t.project_id=? and t.trashed_at is null and t.title operator(extensions.%) ?
)
select id,key,title,status,score from candidates where score>=?
order by score desc,id limit 5
""",
        (row, index) ->
            new Match(
                row.getLong(1),
                row.getString(2),
                row.getString(3),
                row.getString(4),
                row.getDouble(5)),
        title,
        projectId,
        title,
        SCORE_THRESHOLD);
  }
}
