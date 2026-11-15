package com.sprintflowinsight.workspace.sprint;

import com.sprintflowinsight.ApiExceptionHandler;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectRepository;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;

@org.springframework.stereotype.Service
public class SprintApplicationService {
  public SprintApplicationService(
      com.sprintflowinsight.workspace.lifecycle.LifecycleService lifecycle,
      SprintService sprintService,
      ProjectRepository projectRepository,
      ProjectAccessService access) {
    this.lifecycle = lifecycle;
    this.sprintService = sprintService;
    this.projectRepository = projectRepository;
    this.access = access;
  }

  private final com.sprintflowinsight.workspace.lifecycle.LifecycleService lifecycle;

  private static final Set<String> VALID_STATUSES =
      Set.of("Planned", "Active", "Completed", "Cancelled");

  private final SprintService sprintService;

  private final ProjectRepository projectRepository;
  private final ProjectAccessService access;

  public List<Sprint> getAllSprints(Long projectId, HttpSession session) {
    String email = access.email(session);
    if (projectId != null) {
      access.read(projectId, session);
      return sprintService.getSprintsForProject(projectId);
    }
    return sprintService.getSprintsVisibleTo(email);
  }

  public ResponseEntity<?> createSprint(SprintRequest request, HttpSession session) {
    Sprint sprint = request.toEntity();
    if (sprint.getStatus() == null || !VALID_STATUSES.contains(sprint.getStatus())) {
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "Status must be Planned, Active, Completed, or Cancelled.");
    }
    if (sprint.getProjectId() == null || !projectRepository.existsById(sprint.getProjectId())) {
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "Choose an existing project for this sprint.");
    }
    access.write(sprint.getProjectId(), session);
    ResponseEntity<?> dateError = validateSprintDates(sprint);
    if (dateError != null) return dateError;
    String actorEmail = access.email(session);
    return ResponseEntity.ok(sprintService.createSprint(sprint, actorEmail));
  }

  @org.springframework.transaction.annotation.Transactional
  public ResponseEntity<?> updateSprint(
      Long id, SprintRequest request, String ifMatch, HttpSession session) {
    Sprint sprintDetails = request.toEntity();
    if (sprintDetails.getStatus() == null || !VALID_STATUSES.contains(sprintDetails.getStatus())) {
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "Status must be Planned, Active, Completed, or Cancelled.");
    }
    if (sprintDetails.getProjectId() == null
        || !projectRepository.existsById(sprintDetails.getProjectId())) {
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "Choose an existing project for this sprint.");
    }
    access.write(sprintDetails.getProjectId(), session);
    String actorEmail = access.email(session);
    return sprintService
        .getSprintById(id)
        .map(
            sprint -> {
              com.sprintflowinsight.VersionPrecondition.require(ifMatch, sprint.getVersion());
              if (!sprint.getProjectId().equals(sprintDetails.getProjectId()))
                return ApiExceptionHandler.error(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "A sprint cannot move to another project.");
              ResponseEntity<?> dateError = validateSprintDates(sprintDetails);
              if (dateError != null) return dateError;
              return ResponseEntity.ok(
                  sprintService.updateSprint(sprint, sprintDetails, actorEmail));
            })
        .orElseThrow(() -> ApiException.notFound("Sprint not found."));
  }

  public ResponseEntity<Sprint> getSprintById(Long id, HttpSession session) {
    return sprintService
        .getSprintById(id)
        .map(
            sprint -> {
              access.read(sprint.getProjectId(), session);
              return ResponseEntity.ok(sprint);
            })
        .orElseThrow(() -> ApiException.notFound("Sprint not found."));
  }

  @org.springframework.transaction.annotation.Transactional
  public ResponseEntity<?> updateSprintStatus(
      Long id, String status, String ifMatch, HttpSession session) {
    if (!VALID_STATUSES.contains(status)) {
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "Status must be Planned, Active, Completed, or Cancelled.");
    }
    Sprint sprint = sprintService.getSprintById(id).orElse(null);
    if (sprint == null) {
      throw ApiException.notFound("Sprint not found.");
    }
    access.write(sprint.getProjectId(), session);
    com.sprintflowinsight.VersionPrecondition.require(ifMatch, sprint.getVersion());
    String actorEmail = access.email(session);
    return ResponseEntity.ok(sprintService.updateSprintStatus(id, status, actorEmail));
  }

  @org.springframework.transaction.annotation.Transactional
  public ResponseEntity<Void> deleteSprint(Long id, String ifMatch, HttpSession session) {
    Sprint sprint = sprintService.getSprintById(id).orElse(null);
    if (sprint == null) {
      throw ApiException.notFound("Sprint not found.");
    }
    access.write(sprint.getProjectId(), session);
    com.sprintflowinsight.VersionPrecondition.require(ifMatch, sprint.getVersion());
    lifecycle.change("sprint", id, false, session);
    return ResponseEntity.noContent().build();
  }

  private ResponseEntity<?> validateSprintDates(Sprint sprint) {
    LocalDate start = sprint.getStartDate();
    LocalDate end = sprint.getEndDate();
    if (start == null || end == null)
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "Sprint start and end dates are required.");
    if (start != null && end != null && end.isBefore(start))
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "Sprint end date must be on or after its start date.");
    return null;
  }
}
