package com.sprintflowinsight.service;

import com.sprintflowinsight.model.Sprint;
import com.sprintflowinsight.model.Task;
import com.sprintflowinsight.project.ProjectAccessService;
import com.sprintflowinsight.repository.*;
import com.sprintflowinsight.story.UserStory;
import com.sprintflowinsight.story.UserStoryRepository;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class LifecycleService {
  private final TaskRepository tasks;
  private final SprintRepository sprints;
  private final UserStoryRepository stories;
  private final RetrospectiveRepository retrospectives;
  private final ProjectAccessService access;
  private final ProjectMutationService mutations;
  private final ActivityService activity;
  private final TaskService taskService;

  public LifecycleService(
      TaskRepository tasks,
      SprintRepository sprints,
      UserStoryRepository stories,
      RetrospectiveRepository retrospectives,
      ProjectAccessService access,
      ProjectMutationService mutations,
      ActivityService activity,
      TaskService taskService) {
    this.tasks = tasks;
    this.sprints = sprints;
    this.stories = stories;
    this.retrospectives = retrospectives;
    this.access = access;
    this.mutations = mutations;
    this.activity = activity;
    this.taskService = taskService;
  }

  public void change(String kind, Long id, boolean restore, HttpSession session) {
    Long projectId = projectFor(kind, id);
    access.write(projectId, session);
    mutations.lock(projectId);

    Instant now = Instant.now();
    String batch = UUID.randomUUID().toString();
    if (kind.equals("task")) {
      Task task = tasks.findById(id).orElseThrow(this::missing);
      requireState(task.getTrashedAt(), restore);
      if (restore) ensureParents(task.getStoryId(), task.getSprintId());
      task.setTrashedAt(restore ? null : now);
      task.setDeletionBatchId(restore ? null : batch);
      tasks.save(task);
    } else if (kind.equals("story")) {
      UserStory story = stories.findById(id).orElseThrow(this::missing);
      requireState(story.getTrashedAt(), restore);
      if (restore) ensureParents(null, story.getSprintId());
      String previous = story.getDeletionBatchId();
      Instant previousTime = story.getTrashedAt();
      story.setTrashedAt(restore ? null : now);
      story.setDeletionBatchId(restore ? null : batch);
      for (Task task : tasks.findAllByProjectId(projectId))
        if (id.equals(task.getStoryId())
            && included(
                task.getTrashedAt(), task.getDeletionBatchId(), previousTime, previous, restore)) {
          task.setTrashedAt(restore ? null : now);
          task.setDeletionBatchId(restore ? null : batch);
        }
      stories.save(story);
    } else if (kind.equals("sprint")) {
      Sprint sprint = sprints.findById(id).orElseThrow(this::missing);
      requireState(sprint.getTrashedAt(), restore);
      if (restore) mutations.requireSingleActiveSprint(projectId, id, sprint.getStatus());
      String previous = sprint.getDeletionBatchId();
      Instant previousTime = sprint.getTrashedAt();
      var childStories =
          stories.findAllByProjectId(projectId).stream()
              .filter(story -> id.equals(story.getSprintId()))
              .toList();
      var childIds =
          childStories.stream().map(UserStory::getId).collect(java.util.stream.Collectors.toSet());
      for (UserStory story : childStories)
        if (included(
            story.getTrashedAt(), story.getDeletionBatchId(), previousTime, previous, restore)) {
          story.setTrashedAt(restore ? null : now);
          story.setDeletionBatchId(restore ? null : batch);
        }
      for (Task task : tasks.findAllByProjectId(projectId))
        if ((id.equals(task.getSprintId()) || childIds.contains(task.getStoryId()))
            && included(
                task.getTrashedAt(), task.getDeletionBatchId(), previousTime, previous, restore)) {
          task.setTrashedAt(restore ? null : now);
          task.setDeletionBatchId(restore ? null : batch);
        }
      sprint.setTrashedAt(restore ? null : now);
      sprint.setDeletionBatchId(restore ? null : batch);
      sprints.save(sprint);
    }
    activity.record(
        projectId,
        kind.toUpperCase(java.util.Locale.ROOT) + (restore ? "_RESTORED" : "_TRASHED"),
        (restore ? "Restored " : "Moved to trash: ") + kind + " #" + id + ".",
        access.email(session),
        kind,
        id,
        now);
  }

  public void purge(String kind, Long id, HttpSession session) {
    Long projectId = projectFor(kind, id);
    access.owner(projectId, session);
    access.write(projectId, session);
    mutations.lock(projectId);

    Instant trashedAt =
        switch (kind) {
          case "task" -> tasks.findById(id).orElseThrow(this::missing).getTrashedAt();
          case "story" -> stories.findById(id).orElseThrow(this::missing).getTrashedAt();
          case "sprint" -> sprints.findById(id).orElseThrow(this::missing).getTrashedAt();
          default -> throw missing();
        };
    if (trashedAt == null)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Move this item to the recycle bin first.");
    if (kind.equals("task")) taskService.deleteTask(id);
    else if (kind.equals("story")) {
      tasks.clearStoryReference(id);
      stories.deleteById(id);
    } else {
      for (Task task : tasks.findAllByProjectId(projectId))
        if (id.equals(task.getSprintId())) task.setSprintId(null);
      for (UserStory story : stories.findAllByProjectId(projectId))
        if (id.equals(story.getSprintId())) story.setSprintId(null);
      retrospectives.deleteBySprintId(id);
      sprints.deleteById(id);
    }
    activity.record(
        projectId,
        kind.toUpperCase(java.util.Locale.ROOT) + "_PURGED",
        "Permanently deleted " + kind + " #" + id + ".",
        access.email(session),
        kind,
        id,
        Instant.now());
  }

  private boolean included(
      Instant trashedAt, String batch, Instant parentTime, String parentBatch, boolean restore) {
    if (!restore) return trashedAt == null;
    // Timestamp matching preserves restoration of trash created before deletion batch IDs existed.
    return trashedAt != null
        && (parentBatch != null
            ? Objects.equals(parentBatch, batch)
            : parentTime != null && parentTime.equals(trashedAt));
  }

  private void requireState(Instant trashedAt, boolean restore) {
    if (restore == (trashedAt == null))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          restore ? "Item is not in the recycle bin." : "Item is already in the recycle bin.");
  }

  private void ensureParents(Long storyId, Long sprintId) {
    if ((storyId != null
            && stories.findById(storyId).map(s -> s.getTrashedAt() != null).orElse(true))
        || (sprintId != null
            && sprints.findById(sprintId).map(s -> s.getTrashedAt() != null).orElse(true)))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Restore the parent story or sprint first.");
  }

  @Transactional(readOnly = true)
  public Long version(String kind, Long id, HttpSession session) {
    access.read(projectFor(kind, id), session);
    return switch (kind) {
      case "task" -> tasks.findById(id).orElseThrow(this::missing).getVersion();
      case "story" -> stories.findById(id).orElseThrow(this::missing).getVersion();
      case "sprint" -> sprints.findById(id).orElseThrow(this::missing).getVersion();
      default -> throw missing();
    };
  }

  private Long projectFor(String kind, Long id) {
    return switch (kind) {
      case "task" -> tasks.findById(id).orElseThrow(this::missing).getProjectId();
      case "story" -> stories.findById(id).orElseThrow(this::missing).getProjectId();
      case "sprint" -> sprints.findById(id).orElseThrow(this::missing).getProjectId();
      default -> throw missing();
    };
  }

  private ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found.");
  }
}
