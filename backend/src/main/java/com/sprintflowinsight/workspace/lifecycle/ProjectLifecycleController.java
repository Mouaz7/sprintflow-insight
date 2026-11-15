package com.sprintflowinsight.workspace.lifecycle;

import com.sprintflowinsight.VersionPrecondition;
import com.sprintflowinsight.workspace.project.AccessContext;
import com.sprintflowinsight.workspace.project.Project;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectRepository;
import com.sprintflowinsight.workspace.sprint.SprintRepository;
import com.sprintflowinsight.workspace.story.UserStoryRepository;
import com.sprintflowinsight.workspace.task.TaskRepository;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ProjectLifecycleController {
  private final ProjectRepository projects;
  private final ProjectAccessService access;
  private final TaskRepository tasks;
  private final SprintRepository sprints;
  private final UserStoryRepository stories;
  private final LifecycleService lifecycle;

  public ProjectLifecycleController(
      ProjectRepository projects,
      ProjectAccessService access,
      TaskRepository tasks,
      SprintRepository sprints,
      UserStoryRepository stories,
      LifecycleService lifecycle) {
    this.projects = projects;
    this.access = access;
    this.tasks = tasks;
    this.sprints = sprints;
    this.stories = stories;
    this.lifecycle = lifecycle;
  }

  @PutMapping("/projects/{id}/archive")
  @Transactional
  public Project archive(
      @PathVariable Long id,
      @jakarta.validation.Valid @RequestBody ArchiveRequest body,
      HttpSession session) {
    access.require(id, session, AccessContext.Need.MANAGE_ARCHIVED);
    Project project = projects.lockById(id).orElseThrow(this::missing);
    project.setArchivedAt(body.archived() ? Instant.now() : null);
    return projects.save(project);
  }

  @GetMapping("/workspace/trash")
  public List<TrashItem> trash(HttpSession session) {
    String email = access.email(session);
    List<TrashItem> result = new ArrayList<>();
    List<Long> visible = new ArrayList<>();
    for (Project project : projects.findAccessibleIncludingTrash(email)) {
      if (project.getTrashedAt() != null) {
        if (project.getOwnerEmail().equalsIgnoreCase(email))
          result.add(
              new TrashItem(
                  "project",
                  project.getId(),
                  project.getId(),
                  project.getName(),
                  project.getTrashedAt(),
                  project.getVersion()));
      } else visible.add(project.getId());
    }
    if (!visible.isEmpty()) {
      tasks
          .findByProjectIdInAndTrashedAtIsNotNull(visible)
          .forEach(
              t ->
                  result.add(
                      new TrashItem(
                          "task",
                          t.getId(),
                          t.getProjectId(),
                          t.getTitle(),
                          t.getTrashedAt(),
                          t.getVersion())));
      stories
          .findByProjectIdInAndTrashedAtIsNotNull(visible)
          .forEach(
              t ->
                  result.add(
                      new TrashItem(
                          "story",
                          t.getId(),
                          t.getProjectId(),
                          t.getTitle(),
                          t.getTrashedAt(),
                          t.getVersion())));
      sprints
          .findByProjectIdInAndTrashedAtIsNotNull(visible)
          .forEach(
              t ->
                  result.add(
                      new TrashItem(
                          "sprint",
                          t.getId(),
                          t.getProjectId(),
                          t.getName(),
                          t.getTrashedAt(),
                          t.getVersion())));
    }
    return result.stream()
        .sorted(Comparator.comparing(TrashItem::trashedAt).reversed().thenComparing(TrashItem::id))
        .toList();
  }

  @PutMapping("/workspace/trash/{kind}/{id}")
  @Transactional
  public void change(
      @PathVariable String kind,
      @PathVariable Long id,
      @jakarta.validation.Valid @RequestBody TrashRequest body,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    boolean restore = body.restore();
    if (kind.equals("project")) {
      Project project = projects.lockById(id).orElseThrow(this::missing);
      if (!project.getOwnerEmail().equalsIgnoreCase(access.email(session)))
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "Only the project owner can manage deleted projects.");
      VersionPrecondition.require(ifMatch, project.getVersion());
      project.setTrashedAt(restore ? null : Instant.now());
      projects.save(project);
      return;
    }
    VersionPrecondition.require(ifMatch, lifecycle.version(kind, id, session));
    lifecycle.change(kind, id, restore, session);
  }

  @DeleteMapping("/workspace/trash/{kind}/{id}")
  @Transactional
  public void purge(
      @PathVariable String kind,
      @PathVariable Long id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    if (kind.equals("project"))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Use the project delete endpoint to permanently delete a project.");
    VersionPrecondition.require(ifMatch, lifecycle.version(kind, id, session));
    lifecycle.purge(kind, id, session);
  }

  private ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found.");
  }

  public record ArchiveRequest(
      @jakarta.validation.constraints.NotNull(message = "State whether the project is archived.")
          Boolean archived) {}

  public record TrashRequest(
      @jakarta.validation.constraints.NotNull(message = "State whether to restore the item.")
          Boolean restore) {}

  public record TrashItem(
      String kind, Long id, Long projectId, String title, Instant trashedAt, Long version) {}
}
