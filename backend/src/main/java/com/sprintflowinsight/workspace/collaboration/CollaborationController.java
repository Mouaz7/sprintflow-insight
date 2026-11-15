package com.sprintflowinsight.workspace.collaboration;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CollaborationController {
  private final CollaborationService service;

  public CollaborationController(CollaborationService service) {
    this.service = service;
  }

  @GetMapping("/tasks/{taskId}/comments")
  public ResponseEntity<List<TaskComment>> comments(
      @PathVariable Long taskId, HttpSession session) {
    return service.comments(taskId, session);
  }

  @PostMapping("/tasks/{taskId}/comments")
  public ResponseEntity<?> addComment(
      @PathVariable Long taskId, @Valid @RequestBody CommentRequest request, HttpSession session) {
    return service.addComment(taskId, request, session);
  }

  @PutMapping("/tasks/{taskId}/comments/{id}")
  public TaskComment editComment(
      @PathVariable Long taskId,
      @PathVariable Long id,
      @Valid @RequestBody CommentRequest request,
      HttpSession session) {
    return service.editComment(taskId, id, request, session);
  }

  @DeleteMapping("/tasks/{taskId}/comments/{id}")
  public ResponseEntity<Void> deleteComment(
      @PathVariable Long taskId, @PathVariable Long id, HttpSession session) {
    return service.deleteComment(taskId, id, session);
  }
}
