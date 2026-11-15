package com.sprintflowinsight.workspace.task;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
  private final TaskApplicationService service;

  public TaskController(TaskApplicationService service) {
    this.service = service;
  }

  @GetMapping
  public java.util.List<Task> getAllTasks(@RequestParam Long projectId, HttpSession session) {
    return service.getAllTasks(projectId, session);
  }

  @GetMapping("/by-key/{key}")
  public Task getTaskByKey(@PathVariable String key, HttpSession session) {
    return service.getTaskByKey(key, session);
  }

  @GetMapping("/{id}")
  public Task getTask(@PathVariable Long id, HttpSession session) {
    return service.getTask(id, session);
  }

  @PostMapping
  public ResponseEntity<?> createTask(
      @Valid @RequestBody TaskRequest request, HttpSession session) {
    return service.createTask(request, session);
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> updateTask(
      @PathVariable Long id,
      @Valid @RequestBody TaskRequest request,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    return service.updateTask(id, request, ifMatch, session);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> deleteTask(
      @PathVariable Long id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    return service.deleteTask(id, ifMatch, session);
  }

  @PutMapping("/{id}/block")
  public ResponseEntity<?> blockTask(
      @PathVariable Long id,
      @RequestParam String reason,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    return service.blockTask(id, reason, ifMatch, session);
  }

  @PutMapping("/{id}/unblock")
  public ResponseEntity<?> unblockTask(
      @PathVariable Long id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    return service.unblockTask(id, ifMatch, session);
  }
}
