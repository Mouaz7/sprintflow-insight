package com.sprintflowinsight.workspace.lifecycle;

import com.sprintflowinsight.workspace.activity.ActivityService;
import com.sprintflowinsight.workspace.collaboration.RetrospectiveRepository;
import com.sprintflowinsight.workspace.events.TaskDeleted;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectMutationService;
import com.sprintflowinsight.workspace.sprint.Sprint;
import com.sprintflowinsight.workspace.sprint.SprintRepository;
import com.sprintflowinsight.workspace.story.UserStory;
import com.sprintflowinsight.workspace.story.UserStoryRepository;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskRepository;
import com.sprintflowinsight.workspace.task.TaskService;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
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
  private final ApplicationEventPublisher events;

  public LifecycleService(
      TaskRepository tasks,
      SprintRepository sprints,
      UserStoryRepository stories,
      RetrospectiveRepository retrospectives,
      ProjectAccessService access,
      ProjectMutationService mutations,
      ActivityService activity,
      TaskService taskService,
      ApplicationEventPublisher events) {
    this.events = events;
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
      Task saved = tasks.saveAndFlush(task);
      Long actorId = access.userId(session);
      if (restore) taskService.updated(saved, actorId, now);
      else events.publishEvent(new TaskDeleted(UUID.randomUUID(), projectId, actorId, now, id));
    } else if (kind.equals("story")) {
      UserStory story = stories.findById(id).orElseThrow(this::missing);
      requireState(story.getTrashedAt(), restore);
      if (restore) ensureParents(null, story.getSprintId());
      String previous = story.getDeletionBatchId();
      Instant previousTime = story.getTrashedAt();
      if (!restore) tasks.trashWithStory(projectId, id, now, batch);
      else if (previous != null) tasks.restoreBatch(projectId, previous);
      else tasks.restoreLegacy(projectId, id, null, previousTime);
      story = stories.findById(id).orElseThrow(this::missing);
      story.setTrashedAt(restore ? null : now);
      story.setDeletionBatchId(restore ? null : batch);
      stories.save(story);
    } else if (kind.equals("sprint")) {
      Sprint sprint = sprints.findById(id).orElseThrow(this::missing);
      requireState(sprint.getTrashedAt(), restore);
      if (restore) mutations.requireSingleActiveSprint(projectId, id, sprint.getStatus());
      String previous = sprint.getDeletionBatchId();
      Instant previousTime = sprint.getTrashedAt();
      // Tasks first: their selection follows stories' sprint membership, which trashing keeps.
      if (!restore) {
        tasks.trashWithSprint(projectId, id, now, batch);
        stories.trashWithSprint(projectId, id, now, batch);
      } else if (previous != null) {
        tasks.restoreBatch(projectId, previous);
        stories.restoreBatch(projectId, previous);
      } else {
        tasks.restoreLegacy(projectId, null, id, previousTime);
        stories.restoreLegacyForSprint(projectId, id, previousTime);
      }
      sprint = sprints.findById(id).orElseThrow(this::missing);
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
    access.require(
        projectId, session, com.sprintflowinsight.workspace.project.AccessContext.Need.OWNER);
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
      tasks.detachFromSprint(projectId, id);
      stories.detachFromSprint(projectId, id);
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
