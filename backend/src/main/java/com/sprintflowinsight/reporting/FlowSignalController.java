package com.sprintflowinsight.reporting;

import com.sprintflowinsight.reporting.FlowSignalService.FlowSignals;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/flow-signals")
class FlowSignalController {
  private final FlowSignalService service;

  FlowSignalController(FlowSignalService service) {
    this.service = service;
  }

  @GetMapping
  FlowSignals signals(@PathVariable long projectId, HttpSession session) {
    return service.read(projectId, session);
  }
}
