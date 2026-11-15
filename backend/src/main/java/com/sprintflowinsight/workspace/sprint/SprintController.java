package com.sprintflowinsight.workspace.sprint;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sprints")
public class SprintController {
  private final SprintApplicationService service;

  public SprintController(SprintApplicationService service) {
    this.service = service;
  }

  @GetMapping
  public List<Sprint> getAllSprints(
      @RequestParam(required = false) Long projectId, HttpSession session) {
    return service.getAllSprints(projectId, session);
  }

  @PostMapping
  public ResponseEntity<?> createSprint(
      @Valid @RequestBody SprintRequest request, HttpSession session) {
    return service.createSprint(request, session);
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> updateSprint(
      @PathVariable("id") Long id,
      @Valid @RequestBody SprintRequest request,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    return service.updateSprint(id, request, ifMatch, session);
  }

  @GetMapping("/{id}")
  public ResponseEntity<Sprint> getSprintById(@PathVariable("id") Long id, HttpSession session) {
    return service.getSprintById(id, session);
  }

  @PutMapping("/{id}/status")
  public ResponseEntity<?> updateSprintStatus(
      @PathVariable("id") Long id,
      @RequestParam String status,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    return service.updateSprintStatus(id, status, ifMatch, session);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteSprint(
      @PathVariable("id") Long id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    return service.deleteSprint(id, ifMatch, session);
  }
}
