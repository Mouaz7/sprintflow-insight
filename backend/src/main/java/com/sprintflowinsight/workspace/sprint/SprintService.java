package com.sprintflowinsight.workspace.sprint;

import com.sprintflowinsight.workspace.project.ProjectMutationService;
import com.sprintflowinsight.workspace.activity.ActivityService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SprintService {
  public SprintService(
      ProjectMutationService mutations,
      SprintRepository sprintRepository,
      ActivityService activityService) {
    this.mutations = mutations;
    this.sprintRepository = sprintRepository;
    this.activityService = activityService;
  }

  private final ProjectMutationService mutations;

  private final SprintRepository sprintRepository;

  private final ActivityService activityService;

  // GET ALL SPRINT
  public List<Sprint> getSprintsVisibleTo(String email) {
    return sprintRepository.findVisibleTo(email);
  }

  public List<Sprint> getSprintsForProject(Long projectId) {
    return sprintRepository.findByProjectId(projectId);
  }

  @org.springframework.transaction.annotation.Transactional
  public Sprint createSprint(Sprint sprint, String actorEmail) {
    mutations.requireSingleActiveSprint(sprint.getProjectId(), null, sprint.getStatus());

    sprint.setId(null);
    sprint.setVersion(null);
    sprint.setCreatedAt(Instant.now());
    sprint.setCompletedAt(null);
    if ("Completed".equals(sprint.getStatus())) sprint.setCompletedAt(Instant.now());
    Sprint saved = sprintRepository.save(sprint);
    activityService.record(
        saved.getProjectId(),
        "SPRINT_CREATED",
        "Created sprint “" + saved.getName() + "”.",
        actorEmail);
    return saved;
  }

  @org.springframework.transaction.annotation.Transactional
  public Sprint updateSprint(Sprint sprint, Sprint changes, String actorEmail) {
    mutations.requireSingleActiveSprint(sprint.getProjectId(), sprint.getId(), changes.getStatus());

    String before = sprint.getStatus();
    sprint.setName(changes.getName());
    sprint.setGoal(changes.getGoal());
    sprint.setProjectId(changes.getProjectId());
    sprint.setTotalStoryPoints(changes.getTotalStoryPoints());
    sprint.setStartDate(changes.getStartDate());
    sprint.setEndDate(changes.getEndDate());
    sprint.setStatus(changes.getStatus());
    if (!"Completed".equals(before) && "Completed".equals(changes.getStatus()))
      sprint.setCompletedAt(Instant.now());
    if ("Completed".equals(before) && !"Completed".equals(changes.getStatus()))
      sprint.setCompletedAt(null);
    Sprint saved = sprintRepository.save(sprint);
    if (!before.equals(changes.getStatus()))
      activityService.record(
          saved.getProjectId(),
          "SPRINT_STATUS",
          "Moved sprint “" + saved.getName() + "” to " + changes.getStatus() + ".",
          actorEmail);
    return saved;
  }

  // GET ID
  public Optional<Sprint> getSprintById(Long id) {
    return sprintRepository.findById(id).filter(s -> s.getTrashedAt() == null);
  }

  // ( LIFE CYCLE US18 : Planned, Active, Completed, Cancelled)
  @org.springframework.transaction.annotation.Transactional
  public Sprint updateSprintStatus(Long id, String status, String actorEmail) {
    Sprint sprint =
        sprintRepository
            .findById(id)
            .orElseThrow(
                () ->
                    com.sprintflowinsight.common.error.ApiException.notFound("Sprint not found."));
    return updateSprint(sprint, copyWithStatus(sprint, status), actorEmail);
  }

  private Sprint copyWithStatus(Sprint source, String status) {
    Sprint result = new Sprint();
    result.setName(source.getName());
    result.setGoal(source.getGoal());
    result.setProjectId(source.getProjectId());
    result.setTotalStoryPoints(source.getTotalStoryPoints());
    result.setStartDate(source.getStartDate());
    result.setEndDate(source.getEndDate());
    result.setVersion(source.getVersion());
    result.setStatus(status);
    return result;
  }
}
