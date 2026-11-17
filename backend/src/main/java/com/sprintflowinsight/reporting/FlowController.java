package com.sprintflowinsight.reporting;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Flow analytics (B5) and per-column WIP limits. */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class FlowController {
  private final FlowService service;

  public FlowController(FlowService service) {
    this.service = service;
  }

  @GetMapping("/flow")
  public FlowResponse flow(
      @PathVariable Long projectId, @Valid @ModelAttribute FlowQuery query, HttpSession session) {
    return service.flow(projectId, query, session);
  }

  @GetMapping("/wip-limits")
  public List<WipLimitResponse> limits(@PathVariable Long projectId, HttpSession session) {
    return service.limits(projectId, session);
  }

  @PutMapping("/wip-limits")
  public List<WipLimitResponse> replaceLimits(
      @PathVariable Long projectId,
      @Valid @RequestBody WipLimitsRequest request,
      HttpSession session) {
    return service.replaceLimits(projectId, request, session);
  }
}
