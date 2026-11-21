package com.sprintflowinsight.workspace.integration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@OpenAPIDefinition(info = @Info(title = "SprintFlow public API", version = "v1"))
@SecurityScheme(name = "personalToken", type = SecuritySchemeType.HTTP, scheme = "bearer")
@SecurityRequirement(name = "personalToken")
@RestController
@RequestMapping("/api/v1/projects")
class PublicWorkspaceController {
  private final PublicWorkspace workspace;

  PublicWorkspaceController(PublicWorkspace workspace) {
    this.workspace = workspace;
  }

  @GetMapping
  @Operation(
      summary = "List projects visible to the account",
      description = "Requires PROJECT_READ.")
  PublicWorkspace.Page<PublicWorkspace.ProjectView> projects(
      @RequestParam(defaultValue = "0") @Min(0) long after,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
    return workspace.projects(after, size);
  }

  @GetMapping("/{projectId}")
  @Operation(
      summary = "Read a project",
      description = "Requires PROJECT_READ and project membership.")
  PublicWorkspace.ProjectView project(@PathVariable long projectId) {
    return workspace.project(projectId);
  }

  @GetMapping("/{projectId}/tasks")
  @Operation(
      summary = "List project tasks",
      description = "Requires TASK_READ and project membership.")
  PublicWorkspace.Page<PublicWorkspace.TaskView> tasks(
      @PathVariable long projectId,
      @RequestParam(defaultValue = "0") @Min(0) long after,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
    return workspace.tasks(projectId, after, size);
  }

  @GetMapping("/{projectId}/tasks/{taskId}")
  @Operation(
      summary = "Read a project task",
      description = "Requires TASK_READ and project membership.")
  PublicWorkspace.TaskView task(@PathVariable long projectId, @PathVariable long taskId) {
    return workspace.task(projectId, taskId);
  }

  @PostMapping("/{projectId}/tasks")
  @Operation(
      summary = "Create a project task",
      description = "Requires TASK_WRITE and write access to an active project.")
  @ApiResponse(
      responseCode = "201",
      description = "Created",
      content = @Content(schema = @Schema(implementation = PublicWorkspace.TaskView.class)))
  ResponseEntity<?> create(
      @PathVariable long projectId, @Valid @RequestBody PublicWorkspace.NewTask draft) {
    return workspace.create(projectId, draft);
  }
}
