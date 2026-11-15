package com.sprintflowinsight.workspace.attachment;

import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tasks/{taskId}/attachments")
public class TaskAttachmentController {
  private final TaskAttachmentService service;

  public TaskAttachmentController(TaskAttachmentService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<?> list(@PathVariable Long taskId, HttpSession session) {
    return service.list(taskId, session);
  }

  @PostMapping
  public ResponseEntity<?> addLink(
      @PathVariable Long taskId,
      @jakarta.validation.Valid @RequestBody AttachmentRequest request,
      HttpSession session) {
    return service.addLink(taskId, request, session);
  }

  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<?> upload(
      @PathVariable Long taskId, @RequestParam("file") MultipartFile file, HttpSession session) {
    return service.upload(taskId, file, session);
  }

  @GetMapping("/{id}/file")
  public ResponseEntity<Resource> file(
      @PathVariable Long taskId, @PathVariable Long id, HttpSession session) {
    return service.file(taskId, id, session);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(
      @PathVariable Long taskId, @PathVariable Long id, HttpSession session) {
    return service.delete(taskId, id, session);
  }
}
