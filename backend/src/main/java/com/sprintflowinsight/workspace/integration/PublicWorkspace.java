package com.sprintflowinsight.workspace.integration;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.project.AccessContext.Need;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectRepository;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskApplicationService;
import com.sprintflowinsight.workspace.task.TaskRequest;
import com.sprintflowinsight.workspace.task.TaskService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PublicWorkspace {
  record ProjectView(long id, String key, String name, String description, boolean archived) {}

  record TaskView(
      long id,
      long projectId,
      String key,
      String title,
      String description,
      String status,
      int storyPoints,
      long version) {}

  record Page<T>(List<T> items, Long nextAfter) {}

  record NewTask(
      @NotBlank @Size(max = 255) String title,
      @Size(max = 255) String description,
      @Min(0) @Max(1000) int storyPoints) {}

  private final ProjectAccessService access;
  private final ProjectRepository projects;
  private final TaskService tasks;
  private final TaskApplicationService taskApplication;
  private final JdbcTemplate jdbc;

  PublicWorkspace(
      ProjectAccessService access,
      ProjectRepository projects,
      TaskService tasks,
      TaskApplicationService taskApplication,
      JdbcTemplate jdbc) {
    this.access = access;
    this.projects = projects;
    this.tasks = tasks;
    this.taskApplication = taskApplication;
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public Page<ProjectView> projects(long after, int size) {
    var items =
        jdbc.query(
            """
            select p.id,p.key,p.name,p.description,p.archived_at is not null
            from projects p join project_member m on m.project_id=p.id
            where m.user_id=? and p.id>? and p.trashed_at is null order by p.id limit ?
            """,
            (row, index) ->
                new ProjectView(
                    row.getLong(1),
                    row.getString(2),
                    row.getString(3),
                    row.getString(4),
                    row.getBoolean(5)),
            access.userId(null),
            after,
            size + 1);
    return page(items, size, ProjectView::id);
  }

  @Transactional(readOnly = true)
  public ProjectView project(long projectId) {
    access.require(projectId, null, Need.READ);
    var item =
        projects.findById(projectId).orElseThrow(() -> ApiException.notFound("Project not found."));
    return new ProjectView(
        item.getId(),
        item.getKey(),
        item.getName(),
        item.getDescription(),
        item.getArchivedAt() != null);
  }

  @Transactional(readOnly = true)
  public Page<TaskView> tasks(long projectId, long after, int size) {
    access.require(projectId, null, Need.READ);
    var items =
        jdbc.query(
            """
select t.id,t.project_id,p.key||'-'||t.number,t.title,t.description,t.status,t.story_points,t.version
from task t join projects p on p.id=t.project_id
where t.project_id=? and t.id>? and t.trashed_at is null order by t.id limit ?
""",
            (row, index) ->
                new TaskView(
                    row.getLong(1),
                    row.getLong(2),
                    row.getString(3),
                    row.getString(4),
                    row.getString(5),
                    row.getString(6),
                    row.getInt(7),
                    row.getLong(8)),
            projectId,
            after,
            size + 1);
    return page(items, size, TaskView::id);
  }

  @Transactional(readOnly = true)
  public TaskView task(long projectId, long taskId) {
    access.require(projectId, null, Need.READ);
    return tasks
        .gettaskById(taskId)
        .filter(task -> task.getProjectId() == projectId)
        .map(PublicWorkspace::taskView)
        .orElseThrow(() -> ApiException.notFound("Task not found."));
  }

  public ResponseEntity<?> create(long projectId, NewTask draft) {
    var request =
        new TaskRequest(
            draft.title(),
            draft.description(),
            "To Do",
            false,
            null,
            projectId,
            null,
            null,
            null,
            draft.storyPoints(),
            null,
            List.of(),
            List.of());
    var result = taskApplication.createTask(request, null);
    if (!(result.getBody() instanceof Task created)) return result;
    return ResponseEntity.created(
            URI.create("/api/v1/projects/" + projectId + "/tasks/" + created.getId()))
        .body(taskView(created));
  }

  private static TaskView taskView(Task task) {
    return new TaskView(
        task.getId(),
        task.getProjectId(),
        task.getKey(),
        task.getTitle(),
        task.getDescription(),
        task.getStatus(),
        task.getStoryPoints(),
        task.getVersion());
  }

  private static <T> Page<T> page(
      List<T> items, int size, java.util.function.ToLongFunction<T> id) {
    Long next = items.size() > size ? id.applyAsLong(items.get(size - 1)) : null;
    return new Page<>(
        next == null ? List.copyOf(items) : List.copyOf(items.subList(0, size)), next);
  }
}
