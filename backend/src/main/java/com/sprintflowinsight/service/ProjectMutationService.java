package com.sprintflowinsight.service;

import com.sprintflowinsight.project.ProjectRepository;
import com.sprintflowinsight.repository.SprintRepository;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectMutationService {
  private final ProjectRepository projects;
  private final SprintRepository sprints;

  public ProjectMutationService(ProjectRepository projects, SprintRepository sprints) {

    this.projects = projects;
    this.sprints = sprints;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void lock(Long projectId) {
    var project =
        projects
            .lockById(projectId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
    if (project.getTrashedAt() != null || project.getArchivedAt() != null)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Restore or unarchive the project first.");
  }

  public static final String ACTIVE_SPRINT_MESSAGE =
      "Only one sprint can be active in a project at a time.";

  /**
   * Friendly early check under the project lock. The partial unique index
   * {@code sprint_one_active_project_idx} remains the source of truth for concurrent writers.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void requireSingleActiveSprint(Long projectId, Long ignoredId, String status) {
    lock(projectId);
    if ("Active".equals(status)
        && sprints.findByProjectId(projectId).stream()
            .anyMatch(
                sprint -> !sprint.getId().equals(ignoredId) && "Active".equals(sprint.getStatus())))
      throw new ApiException(ErrorCode.CONFLICT, ACTIVE_SPRINT_MESSAGE);
  }
}
