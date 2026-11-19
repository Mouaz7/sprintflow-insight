package com.sprintflowinsight.workspace.story;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.activity.ActivityService;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectRepository;
import com.sprintflowinsight.workspace.sprint.Sprint;
import com.sprintflowinsight.workspace.sprint.SprintRepository;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class UserStoryService {
  public UserStoryService(
      com.sprintflowinsight.workspace.lifecycle.LifecycleService lifecycle,
      UserStoryRepository userStoryRepository,
      ProjectRepository projectRepository,
      ActivityService activityService,
      ProjectAccessService access,
      SprintRepository sprintRepository,
      com.sprintflowinsight.workspace.task.TaskService taskService,
      com.sprintflowinsight.workspace.project.ProjectMutationService mutations) {
    this.lifecycle = lifecycle;
    this.userStoryRepository = userStoryRepository;
    this.projectRepository = projectRepository;
    this.activityService = activityService;
    this.access = access;
    this.sprintRepository = sprintRepository;
    this.taskService = taskService;
    this.mutations = mutations;
  }

  private final com.sprintflowinsight.workspace.lifecycle.LifecycleService lifecycle;

  private final UserStoryRepository userStoryRepository;

  private final ProjectRepository projectRepository;

  private final ActivityService activityService;
  private final ProjectAccessService access;
  private final SprintRepository sprintRepository;
  private final com.sprintflowinsight.workspace.task.TaskService taskService;
  private final com.sprintflowinsight.workspace.project.ProjectMutationService mutations;
  private static final Set<Integer> VALID_STORY_POINTS = Set.of(1, 2, 3, 5, 8);

  public List<UserStory> getStoriesByProject(Long projectId, HttpSession session) {
    access.read(projectId, session);
    return userStoryRepository.findByProjectId(projectId);
  }

  @Transactional
  public List<UserStory> reorder(Long projectId, OrderRequest request, HttpSession session) {
    access.write(projectId, session);
    mutations.lock(projectId);
    List<UserStory> current = userStoryRepository.findByProjectId(projectId);
    var byId =
        current.stream().collect(java.util.stream.Collectors.toMap(UserStory::getId, item -> item));
    var ids =
        request.items().stream().map(OrderItem::id).collect(java.util.stream.Collectors.toSet());
    if (ids.size() != request.items().size() || !ids.equals(byId.keySet()))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT,
          "The backlog changed. Reload it before changing the order.");
    for (OrderItem item : request.items())
      com.sprintflowinsight.VersionPrecondition.require(
          item.version() == null ? null : "\"" + item.version() + "\"",
          byId.get(item.id()).getVersion());
    for (int index = 0; index < request.items().size(); index++)
      byId.get(request.items().get(index).id()).setBacklogPosition(index);
    userStoryRepository.flush();
    activityService.record(
        projectId, "BACKLOG_REORDERED", "Updated backlog order.", access.email(session));
    return userStoryRepository.findByProjectId(projectId);
  }

  public UserStory getUserStory(Long id, HttpSession session) {
    UserStory story =
        userStoryRepository
            .findById(id)
            .filter(item -> item.getTrashedAt() == null)
            .orElseThrow(
                () ->
                    new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Story not found."));
    access.read(story.getProjectId(), session);
    return story;
  }

  @Transactional
  public ResponseEntity<?> createUserStory(StoryRequest request, HttpSession session) {
    UserStory userStory = request.toEntity();
    if (!projectRepository.existsById(userStory.getProjectId())) {
      throw ApiException.notFound("Project not found.");
    }
    access.write(userStory.getProjectId(), session);
    mutations.lock(userStory.getProjectId());

    ResponseEntity<?> invalid = validateStory(userStory);
    if (invalid != null) return invalid;
    String actorEmail = access.email(session);
    userStory.setCreatedAt(Instant.now());
    userStory.setBacklogPosition(userStoryRepository.lastPosition(userStory.getProjectId()) + 1);
    UserStory savedStory = userStoryRepository.save(Objects.requireNonNull(userStory));
    activityService.record(
        savedStory.getProjectId(),
        "STORY_CREATED",
        "Created story “" + savedStory.getTitle() + "”.",
        actorEmail);
    return ResponseEntity.ok(savedStory);
  }

  @Transactional
  public ResponseEntity<?> updateUserStory(
      Long id, StoryRequest request, String ifMatch, HttpSession session) {
    UserStory storyDetails = request.toEntity();
    if (!projectRepository.existsById(storyDetails.getProjectId())) {
      throw ApiException.notFound("Project not found.");
    }
    access.write(storyDetails.getProjectId(), session);
    mutations.lock(storyDetails.getProjectId());

    ResponseEntity<?> invalid = validateStory(storyDetails);
    if (invalid != null) return invalid;
    String actorEmail = access.email(session);
    return userStoryRepository
        .findById(id)
        .filter(story -> story.getTrashedAt() == null)
        .map(
            story -> {
              com.sprintflowinsight.VersionPrecondition.require(ifMatch, story.getVersion());
              if (!story.getProjectId().equals(storyDetails.getProjectId()))
                throw ApiException.badRequest("A story cannot move to another project.");
              story.setTitle(storyDetails.getTitle());
              story.setDescription(storyDetails.getDescription());
              story.setAcceptanceCriteria(storyDetails.getAcceptanceCriteria());
              story.setStoryPoints(storyDetails.getStoryPoints());
              story.setPriorityId(storyDetails.getPriorityId());
              story.setBusinessValue(storyDetails.getBusinessValue());
              story.setProjectId(storyDetails.getProjectId());
              if (!Objects.equals(story.getSprintId(), storyDetails.getSprintId()))
                taskService.moveStoryTasks(
                    story.getProjectId(), story.getId(), storyDetails.getSprintId(), actorEmail);
              story.setSprintId(storyDetails.getSprintId());
              UserStory saved = userStoryRepository.save(story);
              activityService.record(
                  saved.getProjectId(),
                  "STORY_UPDATED",
                  "Updated story “" + saved.getTitle() + "”.",
                  actorEmail);
              return ResponseEntity.ok(saved);
            })
        .orElseThrow(() -> ApiException.notFound("Story not found."));
  }

  @Transactional
  public ResponseEntity<Void> deleteUserStory(Long id, String ifMatch, HttpSession session) {
    UserStory story =
        userStoryRepository.findById(id).filter(item -> item.getTrashedAt() == null).orElse(null);
    if (story == null) {
      throw ApiException.notFound("Story not found.");
    }
    access.write(story.getProjectId(), session);
    com.sprintflowinsight.VersionPrecondition.require(ifMatch, story.getVersion());
    String actorEmail = access.email(session);
    lifecycle.change("story", id, false, session);
    return ResponseEntity.noContent().build();
  }

  /**
   * Writes an estimate agreed in planning poker (B7) and logs it. The caller has checked write
   * access and holds the project lock.
   */
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public UserStory applyEstimate(
      Long projectId, Long storyId, int storyPoints, Long expectedVersion, String actorEmail) {
    if (!VALID_STORY_POINTS.contains(storyPoints))
      throw ApiException.badRequest("Story points must be 1, 2, 3, 5, or 8.");
    UserStory story =
        userStoryRepository
            .findById(storyId)
            .filter(item -> item.getTrashedAt() == null && projectId.equals(item.getProjectId()))
            .orElseThrow(() -> ApiException.notFound("Story not found."));
    com.sprintflowinsight.VersionPrecondition.require(
        "\"" + expectedVersion + "\"", story.getVersion());
    int previous = story.getStoryPoints();
    story.setStoryPoints(storyPoints);
    UserStory saved = userStoryRepository.saveAndFlush(story);
    activityService.record(
        projectId,
        "STORY_ESTIMATED",
        "Estimated story “"
            + saved.getTitle()
            + "” at "
            + storyPoints
            + " points in planning poker (was "
            + previous
            + ").",
        actorEmail,
        "story",
        storyId,
        Instant.now());
    return saved;
  }

  private ResponseEntity<?> validateStory(UserStory story) {
    if (!VALID_STORY_POINTS.contains(story.getStoryPoints()))
      return com.sprintflowinsight.ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "Story points must be 1, 2, 3, 5, or 8.");
    if (story.getBusinessValue() != null
        && (story.getBusinessValue() < 1 || story.getBusinessValue() > 5))
      return com.sprintflowinsight.ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "Business value must be between 1 and 5.");
    if (story.getSprintId() != null) {
      Sprint sprint = sprintRepository.findById(story.getSprintId()).orElse(null);
      if (sprint == null
          || sprint.getTrashedAt() != null
          || !story.getProjectId().equals(sprint.getProjectId()))
        return com.sprintflowinsight.ApiExceptionHandler.error(
            org.springframework.http.HttpStatus.BAD_REQUEST,
            "Choose a sprint in the same project as this story.");
    }
    return null;
  }
}
