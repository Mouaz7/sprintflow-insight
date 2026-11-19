package com.sprintflowinsight.workspace.dashboard;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/dashboard")
public class DashboardController {
  private final DashboardService service;

  public DashboardController(DashboardService service) {
    this.service = service;
  }

  @GetMapping
  public DashboardResponse read(@PathVariable Long projectId, HttpSession session) {
    return service.read(projectId, session);
  }

  @PutMapping
  public DashboardResponse save(
      @PathVariable Long projectId,
      @Valid @RequestBody DashboardRequest request,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    return service.save(projectId, request, ifMatch, session);
  }
}
