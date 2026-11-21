package com.sprintflowinsight.workspace.offline;

import com.sprintflowinsight.VersionPrecondition;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.collaboration.CollaborationService;
import com.sprintflowinsight.workspace.collaboration.CommentRequest;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectMutationService;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskApplicationService;
import com.sprintflowinsight.workspace.task.TaskRepository;
import com.sprintflowinsight.workspace.task.TaskRequest;
import com.sprintflowinsight.workspace.time.TimeEntryRequest;
import com.sprintflowinsight.workspace.time.TimeTrackingService;
import jakarta.servlet.http.HttpSession;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
class OfflineOperationService {
  private final ProjectAccessService access;
  private final ProjectMutationService mutations;
  private final TaskRepository tasks;
  private final TaskApplicationService taskCommands;
  private final CollaborationService comments;
  private final TimeTrackingService time;
  private final JdbcTemplate jdbc;
  private final JsonMapper json;

  OfflineOperationService(
      ProjectAccessService access,
      ProjectMutationService mutations,
      TaskRepository tasks,
      TaskApplicationService taskCommands,
      CollaborationService comments,
      TimeTrackingService time,
      JdbcTemplate jdbc,
      JsonMapper json) {
    this.access = access;
    this.mutations = mutations;
    this.tasks = tasks;
    this.taskCommands = taskCommands;
    this.comments = comments;
    this.time = time;
    this.jdbc = jdbc;
    this.json = json;
  }

  @Transactional
  public OfflineOperationController.Result replay(
      long projectId, OfflineOperationRequest request, String ifMatch, HttpSession session) {
    long userId = access.userId(session);
    if (!Objects.equals(userId, request.accountId()))
      throw ApiException.forbidden("This queued operation belongs to a different account.");
    access.write(projectId, session);
    mutations.lock(projectId);
    access.forget(projectId);
    access.write(projectId, session);

    byte[] fingerprint = fingerprint(request);
    var recorded =
        jdbc
            .query(
                """
                select fingerprint,task_version from offline_operation
                where user_id=? and project_id=? and operation_id=?
                """,
                (row, index) ->
                    new Receipt(row.getBytes("fingerprint"), row.getLong("task_version")),
                userId,
                projectId,
                request.operationId())
            .stream()
            .findFirst();
    if (recorded.isPresent()) {
      var receipt = recorded.get();
      if (!MessageDigest.isEqual(receipt.fingerprint(), fingerprint))
        throw ApiException.conflict("This operation identifier has already been used.");
      return new OfflineOperationController.Result(receipt.taskVersion());
    }

    Task task =
        tasks
            .findById(request.taskId())
            .filter(
                candidate ->
                    Objects.equals(candidate.getProjectId(), projectId)
                        && candidate.getTrashedAt() == null)
            .orElseThrow(() -> ApiException.notFound("Task not found."));
    VersionPrecondition.require(ifMatch, task.getVersion());
    long version = apply(task, request, ifMatch, session);
    jdbc.update(
        """
        insert into offline_operation(user_id,project_id,operation_id,fingerprint,task_version)
        values (?,?,?,?,?)
        """,
        userId,
        projectId,
        request.operationId(),
        fingerprint,
        version);
    return new OfflineOperationController.Result(version);
  }

  private long apply(
      Task task, OfflineOperationRequest request, String ifMatch, HttpSession session) {
    switch (request.kind()) {
      case MOVE -> {
        var change =
            new TaskRequest(
                task.getTitle(),
                task.getDescription(),
                request.status(),
                task.isBlocked(),
                task.getBlockReason(),
                task.getProjectId(),
                task.getSprintId(),
                task.getStoryId(),
                task.getAssigneeEmail(),
                task.getStoryPoints(),
                task.getDueAt(),
                task.getLabels(),
                task.getChecklist());
        var result = taskCommands.updateTask(task.getId(), change, ifMatch, session);
        if (result.getBody() instanceof Task saved) return saved.getVersion();
        throw ApiException.conflict("The task cannot move with its current settings.");
      }
      case COMMENT ->
          comments.addComment(task.getId(), new CommentRequest(request.message(), null), session);
      case LOG_TIME -> {
        var log = request.time();
        time.log(
            task.getProjectId(),
            new TimeEntryRequest(task.getId(), log.minutes(), log.spentOn(), log.note(), null),
            session);
      }
    }
    return task.getVersion();
  }

  private byte[] fingerprint(OfflineOperationRequest request) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable.", impossible);
    }
  }

  private record Receipt(byte[] fingerprint, long taskVersion) {}
}
