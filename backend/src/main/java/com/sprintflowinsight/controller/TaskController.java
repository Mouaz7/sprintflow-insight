package com.sprintflowinsight.controller;

import com.sprintflowinsight.ApiExceptionHandler;
import com.sprintflowinsight.model.Sprint;
import com.sprintflowinsight.model.Task;
import com.sprintflowinsight.project.Project;
import com.sprintflowinsight.project.ProjectAccessService;
import com.sprintflowinsight.project.ProjectMemberRepository;
import com.sprintflowinsight.project.ProjectRepository;
import com.sprintflowinsight.repository.SprintRepository;
import com.sprintflowinsight.service.TaskService;
import com.sprintflowinsight.story.UserStory;
import com.sprintflowinsight.story.UserStoryRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
  private final com.sprintflowinsight.service.LifecycleService lifecycle;
  private static final Set<String> VALID_STATUSES =
      Set.of("To Do", "In Progress", "Review", "Done");

  private final TaskService taskService;
  private final com.sprintflowinsight.service.ProjectMutationService mutations;
  private final ProjectRepository projects;
  private final ProjectMemberRepository members;
  private final SprintRepository sprints;
  private final UserStoryRepository stories;
  private final ProjectAccessService access;

  public TaskController(
      TaskService taskService,
      com.sprintflowinsight.service.ProjectMutationService mutations,
      ProjectRepository projects,
      ProjectMemberRepository members,
      SprintRepository sprints,
      UserStoryRepository stories,
      ProjectAccessService access,
      com.sprintflowinsight.service.LifecycleService lifecycle) {
    this.lifecycle = lifecycle;

    this.taskService = taskService;
    this.mutations = mutations;
    this.projects = projects;
    this.members = members;
    this.sprints = sprints;
    this.stories = stories;
    this.access = access;
  }

  @GetMapping
  public java.util.List<Task> getAllTasks(@RequestParam Long projectId, HttpSession session) {
    access.read(projectId, session);
    return taskService.getTasksForProject(projectId);
  }

  @GetMapping("/{id}")
  public Task getTask(@PathVariable Long id, HttpSession session) {
    Task task =
        taskService
            .gettaskById(id)
            .orElseThrow(
                () ->
                    new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Task not found."));
    access.read(task.getProjectId(), session);
    return task;
  }

  @org.springframework.transaction.annotation.Transactional
  @PostMapping
  public ResponseEntity<?> createTask(@Valid @RequestBody Task task, HttpSession session) {
    if (task.getStatus() == null || !VALID_STATUSES.contains(task.getStatus()))
      return error("Status must be To Do, In Progress, Review, or Done.");
    if (task.getProjectId() == null || !projects.existsById(task.getProjectId()))
      return error("Choose an existing project for this task.");
    access.write(task.getProjectId(), session);
    mutations.lock(task.getProjectId());
    ResponseEntity<?> invalid = validateAssociations(task);
    if (invalid != null) return invalid;
    return ResponseEntity.ok(taskService.createTask(task, access.email(session)));
  }

  @org.springframework.transaction.annotation.Transactional
  @PutMapping("/{id}")
  public ResponseEntity<?> updateTask(
      @PathVariable Long id,
      @Valid @RequestBody Task changes,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    if (changes.getStatus() == null || !VALID_STATUSES.contains(changes.getStatus()))
      return error("Status must be To Do, In Progress, Review, or Done.");
    Task task = taskService.gettaskById(id).orElse(null);
    if (task == null) return ResponseEntity.notFound().build();
    access.write(task.getProjectId(), session);
    mutations.lock(task.getProjectId());
    com.sprintflowinsight.VersionPrecondition.require(ifMatch, task.getVersion());
    if (changes.getProjectId() == null || !task.getProjectId().equals(changes.getProjectId()))
      return error("A task cannot move to another project.");
    ResponseEntity<?> invalid = validateAssociations(changes);
    if (invalid != null) return invalid;
    return ResponseEntity.ok(taskService.updateTask(task, changes, access.email(session)));
  }

  @org.springframework.transaction.annotation.Transactional
  @DeleteMapping("/{id}")
  public ResponseEntity<?> deleteTask(
      @PathVariable Long id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    Task task = taskService.gettaskById(id).orElse(null);
    if (task == null) return ResponseEntity.notFound().build();
    access.write(task.getProjectId(), session);
    mutations.lock(task.getProjectId());
    com.sprintflowinsight.VersionPrecondition.require(ifMatch, task.getVersion());
    lifecycle.change("task", id, false, session);
    return ResponseEntity.noContent().build();
  }

  @org.springframework.transaction.annotation.Transactional
  @PutMapping("/{id}/block")
  public ResponseEntity<?> blockTask(
      @PathVariable Long id,
      @RequestParam String reason,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    Task task = taskService.gettaskById(id).orElse(null);
    if (task == null) return ResponseEntity.notFound().build();
    access.write(task.getProjectId(), session);
    mutations.lock(task.getProjectId());
    com.sprintflowinsight.VersionPrecondition.require(ifMatch, task.getVersion());
    if (reason == null || reason.isBlank())
      return error("Provide a reason for blocking this task.");
    return ResponseEntity.ok(taskService.blockTask(id, reason.trim(), access.email(session)));
  }

  @org.springframework.transaction.annotation.Transactional
  @PutMapping("/{id}/unblock")
  public ResponseEntity<?> unblockTask(
      @PathVariable Long id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    Task task = taskService.gettaskById(id).orElse(null);
    if (task == null) return ResponseEntity.notFound().build();
    access.write(task.getProjectId(), session);
    mutations.lock(task.getProjectId());
    com.sprintflowinsight.VersionPrecondition.require(ifMatch, task.getVersion());
    return ResponseEntity.ok(taskService.unblockTask(id, access.email(session)));
  }

  private ResponseEntity<?> validateAssociations(Task task) {
    if (task.isBlocked() && (task.getBlockReason() == null || task.getBlockReason().isBlank()))
      return error("Provide a reason for blocking this task.");
    task.setBlockReason(task.isBlocked() ? task.getBlockReason().trim() : null);
    if ("Done".equals(task.getStatus())
        && task.getChecklist().stream().anyMatch(step -> step.required() && !step.completed()))
      return error("Complete all required checklist steps before marking the task Done.");
    if (task.getLabels().stream().distinct().count() != task.getLabels().size())
      return error("Labels must be unique.");
    if (task.getChecklist().stream()
            .map(com.sprintflowinsight.model.ChecklistStep::id)
            .distinct()
            .count()
        != task.getChecklist().size()) return error("Checklist steps must have unique IDs.");
    Long projectId = task.getProjectId();
    if (task.getSprintId() != null) {
      Optional<Sprint> sprint = sprints.findById(task.getSprintId());
      if (sprint.isEmpty()
          || sprint.get().getTrashedAt() != null
          || !projectId.equals(sprint.get().getProjectId()))
        return error("Choose a sprint in the same project as this task.");
    }
    if (task.getStoryId() != null) {
      Optional<UserStory> story = stories.findById(task.getStoryId());
      if (story.isEmpty()
          || story.get().getTrashedAt() != null
          || !projectId.equals(story.get().getProjectId()))
        return error("Choose a story in the same project as this task.");
      if (!Objects.equals(story.get().getSprintId(), task.getSprintId()))
        return error("A task must use the same sprint as its user story.");
    }
    if (task.getAssigneeEmail() != null && !task.getAssigneeEmail().isBlank()) {
      String assignee = task.getAssigneeEmail().trim();
      Project project = projects.findById(projectId).orElseThrow();
      boolean isOwner = project.getOwnerEmail().equalsIgnoreCase(assignee);
      if (!isOwner && members.findByProjectIdAndEmailIgnoreCase(projectId, assignee).isEmpty())
        return error("Assign this task to a member of the same project.");
      task.setAssigneeEmail(assignee.toLowerCase());
    } else {
      task.setAssigneeEmail(null);
    }
    return null;
  }

  private ResponseEntity<?> error(String message) {
    return ApiExceptionHandler.error(HttpStatus.BAD_REQUEST, message);
  }
}
