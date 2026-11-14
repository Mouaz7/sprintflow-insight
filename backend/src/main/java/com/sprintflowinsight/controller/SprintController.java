package com.sprintflowinsight.controller;

import com.sprintflowinsight.ApiExceptionHandler;
import com.sprintflowinsight.model.Sprint;
import com.sprintflowinsight.project.ProjectAccessService;
import com.sprintflowinsight.project.ProjectRepository;
import com.sprintflowinsight.service.SprintService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sprints")
public class SprintController {
  public SprintController(
      com.sprintflowinsight.service.LifecycleService lifecycle,
      SprintService sprintService,
      ProjectRepository projectRepository,
      ProjectAccessService access) {
    this.lifecycle = lifecycle;
    this.sprintService = sprintService;
    this.projectRepository = projectRepository;
    this.access = access;
  }

  private final com.sprintflowinsight.service.LifecycleService lifecycle;

  private static final Set<String> VALID_STATUSES =
      Set.of("Planned", "Active", "Completed", "Cancelled");

  private final SprintService sprintService;

  private final ProjectRepository projectRepository;
  private final ProjectAccessService access;

  @GetMapping
  public List<Sprint> getAllSprints(
      @RequestParam(required = false) Long projectId, HttpSession session) {
    String email = access.email(session);
    if (projectId != null) {
      access.read(projectId, session);
      return sprintService.getSprintsForProject(projectId);
    }
    return sprintService.getAllSprints().stream()
        .filter(
            s -> {
              try {
                access.roleFor(s.getProjectId(), email);
                return true;
              } catch (Exception ignored) {
                return false;
              }
            })
        .toList();
  }

  @PostMapping
  public ResponseEntity<?> createSprint(@Valid @RequestBody Sprint sprint, HttpSession session) {
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
    ResponseEntity<?> activeError =
        validateSingleActiveSprint(sprint.getProjectId(), null, sprint.getStatus());
    if (activeError != null) return activeError;
    String actorEmail = access.email(session);
    return ResponseEntity.ok(sprintService.createSprint(sprint, actorEmail));
  }

  @org.springframework.transaction.annotation.Transactional
  @PutMapping("/{id}")
  public ResponseEntity<?> updateSprint(
      @PathVariable("id") Long id,
      @Valid @RequestBody Sprint sprintDetails,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
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
              ResponseEntity<?> activeError =
                  validateSingleActiveSprint(
                      sprintDetails.getProjectId(), id, sprintDetails.getStatus());
              if (activeError != null) return activeError;
              return ResponseEntity.ok(
                  sprintService.updateSprint(sprint, sprintDetails, actorEmail));
            })
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/{id}")
  public ResponseEntity<Sprint> getSprintById(@PathVariable("id") Long id, HttpSession session) {
    return sprintService
        .getSprintById(id)
        .map(
            sprint -> {
              access.read(sprint.getProjectId(), session);
              return ResponseEntity.ok(sprint);
            })
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @org.springframework.transaction.annotation.Transactional
  @PutMapping("/{id}/status")
  public ResponseEntity<?> updateSprintStatus(
      @PathVariable("id") Long id,
      @RequestParam String status,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    if (!VALID_STATUSES.contains(status)) {
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "Status must be Planned, Active, Completed, or Cancelled.");
    }
    Sprint sprint = sprintService.getSprintById(id).orElse(null);
    if (sprint == null) {
      return ResponseEntity.notFound().build();
    }
    access.write(sprint.getProjectId(), session);
    com.sprintflowinsight.VersionPrecondition.require(ifMatch, sprint.getVersion());
    ResponseEntity<?> activeError = validateSingleActiveSprint(sprint.getProjectId(), id, status);
    if (activeError != null) return activeError;
    String actorEmail = access.email(session);
    return ResponseEntity.ok(sprintService.updateSprintStatus(id, status, actorEmail));
  }

  @org.springframework.transaction.annotation.Transactional
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteSprint(
      @PathVariable("id") Long id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    Sprint sprint = sprintService.getSprintById(id).orElse(null);
    if (sprint == null) {
      return ResponseEntity.notFound().build();
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

  private ResponseEntity<?> validateSingleActiveSprint(
      Long projectId, Long ignoredSprintId, String requestedStatus) {
    if (!"Active".equals(requestedStatus)) return null;
    boolean anotherActive =
        sprintService.getSprintsForProject(projectId).stream()
            .anyMatch(
                existing ->
                    "Active".equals(existing.getStatus())
                        && !existing.getId().equals(ignoredSprintId));
    if (anotherActive)
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.CONFLICT,
          "Only one sprint can be active in a project at a time.");
    return null;
  }
}
