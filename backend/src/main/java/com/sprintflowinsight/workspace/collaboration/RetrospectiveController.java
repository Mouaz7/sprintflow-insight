package com.sprintflowinsight.workspace.collaboration;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/retrospective")
public class RetrospectiveController {
  private final RetrospectiveService service;

  public RetrospectiveController(RetrospectiveService service) {
    this.service = service;
  }

  @GetMapping
  public List<RetrospectiveEntry> list(
      @PathVariable Long projectId, @RequestParam Long sprintId, HttpSession session) {
    return service.list(projectId, sprintId, session);
  }

  @PostMapping
  public RetrospectiveEntry add(
      @PathVariable Long projectId, @Valid @RequestBody EntryRequest request, HttpSession session) {
    return service.add(projectId, request, session);
  }

  @PutMapping("/{id}")
  public RetrospectiveEntry update(
      @PathVariable Long projectId,
      @PathVariable Long id,
      @Valid @RequestBody EntryRequest request,
      HttpSession session) {
    return service.update(projectId, id, request, session);
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable Long projectId, @PathVariable Long id, HttpSession session) {
    service.delete(projectId, id, session);
  }
}
