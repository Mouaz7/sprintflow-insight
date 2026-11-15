package com.sprintflowinsight.workspace.project;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/templates")
public class ProjectTemplateController {
  private final ProjectTemplateService service;

  public ProjectTemplateController(ProjectTemplateService service) {
    this.service = service;
  }

  @PostMapping
  public Project create(@Valid @RequestBody TemplateRequest request, HttpSession session) {
    return service.create(request, session);
  }
}
