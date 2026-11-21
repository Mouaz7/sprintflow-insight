package com.sprintflowinsight.risk.live;

import com.sprintflowinsight.risk.live.TaskRiskService.ProjectRisk;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/task-risk")
class TaskRiskController {
  private final TaskRiskService service;

  TaskRiskController(TaskRiskService service) {
    this.service = service;
  }

  @GetMapping
  ProjectRisk risk(@PathVariable long projectId, HttpSession session) {
    return service.read(projectId, session);
  }
}
