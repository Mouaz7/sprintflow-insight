package com.sprintflowinsight.service;

import com.sprintflowinsight.project.ProjectRepository;
import com.sprintflowinsight.repository.SprintRepository;
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

  @Transactional(propagation = Propagation.MANDATORY)
  public void requireSingleActiveSprint(Long projectId, Long ignoredId, String status) {
    lock(projectId);
    if ("Active".equals(status)
        && sprints.findByProjectId(projectId).stream()
            .anyMatch(
                sprint -> !sprint.getId().equals(ignoredId) && "Active".equals(sprint.getStatus())))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Only one sprint can be active in a project at a time.");
  }
}
