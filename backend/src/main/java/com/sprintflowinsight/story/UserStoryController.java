package com.sprintflowinsight.story;

import com.sprintflowinsight.model.Sprint;
import com.sprintflowinsight.project.ProjectAccessService;
import com.sprintflowinsight.project.ProjectRepository;
import com.sprintflowinsight.repository.SprintRepository;
import com.sprintflowinsight.service.ActivityService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stories")
public class UserStoryController {
  public UserStoryController(
      com.sprintflowinsight.service.LifecycleService lifecycle,
      UserStoryRepository userStoryRepository,
      ProjectRepository projectRepository,
      ActivityService activityService,
      ProjectAccessService access,
      SprintRepository sprintRepository,
      com.sprintflowinsight.service.TaskService taskService,
      com.sprintflowinsight.service.ProjectMutationService mutations) {
    this.lifecycle = lifecycle;
    this.userStoryRepository = userStoryRepository;
    this.projectRepository = projectRepository;
    this.activityService = activityService;
    this.access = access;
    this.sprintRepository = sprintRepository;
    this.taskService = taskService;
    this.mutations = mutations;
  }

  private final com.sprintflowinsight.service.LifecycleService lifecycle;

  private final UserStoryRepository userStoryRepository;

  private final ProjectRepository projectRepository;

  private final ActivityService activityService;
  private final ProjectAccessService access;
  private final SprintRepository sprintRepository;
  private final com.sprintflowinsight.service.TaskService taskService;
  private final com.sprintflowinsight.service.ProjectMutationService mutations;
  private static final Set<Integer> VALID_STORY_POINTS = Set.of(1, 2, 3, 5, 8);

  @GetMapping("/project/{projectId}")
  public List<UserStory> getStoriesByProject(@PathVariable Long projectId, HttpSession session) {
    access.read(projectId, session);
    return userStoryRepository.findByProjectId(projectId);
  }

  @PutMapping("/project/{projectId}/order")
  @Transactional
  public List<UserStory> reorder(
      @PathVariable Long projectId, @Valid @RequestBody OrderRequest request, HttpSession session) {
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

  public record OrderItem(@jakarta.validation.constraints.NotNull Long id, Long version) {}

  public record OrderRequest(
      @jakarta.validation.constraints.NotNull @jakarta.validation.Valid
          List<@jakarta.validation.constraints.NotNull @jakarta.validation.Valid OrderItem>
              items) {}

  @GetMapping("/{id}")
  public UserStory getUserStory(@PathVariable Long id, HttpSession session) {
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

  @PostMapping
  @Transactional
  public ResponseEntity<?> createUserStory(
      @Valid @RequestBody UserStory userStory, HttpSession session) {
    if (userStory.getProjectId() == null
        || !projectRepository.existsById(userStory.getProjectId())) {
      return ResponseEntity.notFound().build();
    }
    access.write(userStory.getProjectId(), session);
    mutations.lock(userStory.getProjectId());

    ResponseEntity<?> invalid = validateStory(userStory);
    if (invalid != null) return invalid;
    String actorEmail = access.email(session);
    userStory.setId(null);
    userStory.setVersion(null);
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
  @PutMapping("/{id}")
  public ResponseEntity<?> updateUserStory(
      @PathVariable("id") Long id,
      @Valid @RequestBody UserStory storyDetails,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    if (!projectRepository.existsById(storyDetails.getProjectId())) {
      return ResponseEntity.notFound().build();
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
                return ResponseEntity.badRequest()
                    .body(java.util.Map.of("message", "A story cannot move to another project."));
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
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  @Transactional
  public ResponseEntity<Void> deleteUserStory(
      @PathVariable("id") Long id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    UserStory story =
        userStoryRepository.findById(id).filter(item -> item.getTrashedAt() == null).orElse(null);
    if (story == null) {
      return ResponseEntity.notFound().build();
    }
    access.write(story.getProjectId(), session);
    com.sprintflowinsight.VersionPrecondition.require(ifMatch, story.getVersion());
    String actorEmail = access.email(session);
    lifecycle.change("story", id, false, session);
    return ResponseEntity.noContent().build();
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
