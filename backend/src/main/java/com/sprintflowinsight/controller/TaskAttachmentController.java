package com.sprintflowinsight.controller;

import com.sprintflowinsight.common.error.ApiException;

import com.sprintflowinsight.model.Task;
import com.sprintflowinsight.model.TaskAttachment;
import com.sprintflowinsight.project.ProjectAccessService;
import com.sprintflowinsight.repository.TaskAttachmentRepository;
import com.sprintflowinsight.service.TaskService;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tasks/{taskId}/attachments")
public class TaskAttachmentController {
  private static final Path UPLOAD_DIRECTORY =
      Path.of("data", "attachments").toAbsolutePath().normalize();
  private final com.sprintflowinsight.service.UploadValidator validator;
  private final com.sprintflowinsight.service.AttachmentStorageService storage;
  private final TaskService tasks;
  private final TaskAttachmentRepository attachments;
  private final ProjectAccessService access;

  public TaskAttachmentController(
      TaskService tasks,
      TaskAttachmentRepository attachments,
      ProjectAccessService access,
      com.sprintflowinsight.service.UploadValidator validator,
      com.sprintflowinsight.service.AttachmentStorageService storage) {
    this.validator = validator;
    this.storage = storage;

    this.tasks = tasks;
    this.attachments = attachments;
    this.access = access;
  }

  @GetMapping
  public ResponseEntity<?> list(@PathVariable Long taskId, HttpSession session) {
    Task task = findTask(taskId);
    if (task == null) throw ApiException.notFound("Task not found.");
    access.read(task.getProjectId(), session);
    return ResponseEntity.ok(attachments.findByTaskIdOrderByCreatedAtAsc(taskId));
  }

  @PostMapping
  public ResponseEntity<?> addLink(
      @PathVariable Long taskId,
      @jakarta.validation.Valid @RequestBody AttachmentRequest request,
      HttpSession session) {
    Task task = findTask(taskId);
    if (task == null) throw ApiException.notFound("Task not found.");
    access.write(task.getProjectId(), session);
    try {
      URI uri = URI.create(request.url());
      if (uri.getScheme() == null
          || !(uri.getScheme().equals("https") || uri.getScheme().equals("http")))
        throw new IllegalArgumentException();
    } catch (Exception ex) {
      throw ApiException.badRequest("Attachment link must start with https:// or http://.");
    }
    TaskAttachment item = new TaskAttachment();
    item.setTaskId(taskId);
    item.setProjectId(task.getProjectId());
    item.setName(
        request.name() == null || request.name().isBlank() ? request.url() : request.name().trim());
    item.setUrl(request.url().trim());
    item.setCreatedAt(Instant.now());
    return ResponseEntity.ok(attachments.save(item));
  }

  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<?> upload(
      @PathVariable Long taskId, @RequestParam("file") MultipartFile file, HttpSession session) {
    Task task = findTask(taskId);
    if (task == null) throw ApiException.notFound("Task not found.");
    access.write(task.getProjectId(), session);
    if (file.isEmpty() || file.getSize() > com.sprintflowinsight.service.UploadValidator.MAX_BYTES)
      throw ApiException.badRequest("File must be between 1 byte and 15 MB.");
    String safeName =
        Optional.ofNullable(file.getOriginalFilename())
            .orElse("Attachment")
            .replaceAll("[\\p{Cntrl}\\\\/\"]", "");
    if (safeName.isBlank()) safeName = "Attachment";
    if (safeName.length() > 200) safeName = safeName.substring(0, 200);
    try {
      byte[] bytes = file.getBytes();
      String extension = validator.validate(bytes);
      return ResponseEntity.ok(storage.store(task, bytes, extension, safeName, session));
    } catch (java.io.IOException error) {
      throw new ApiException(
          com.sprintflowinsight.common.error.ErrorCode.INTERNAL_ERROR, "Could not store the attachment.");
    }
  }

  @GetMapping("/{id}/file")
  public ResponseEntity<Resource> file(
      @PathVariable Long taskId, @PathVariable Long id, HttpSession session) {
    Task task = findTask(taskId);
    if (task == null) throw ApiException.notFound("Task not found.");
    access.read(task.getProjectId(), session);
    TaskAttachment item =
        attachments
            .findById(id)
            .filter(a -> taskId.equals(a.getTaskId()) && a.getStorageKey() != null)
            .orElse(null);
    if (item == null) throw ApiException.notFound("Attachment not found.");
    Path path = UPLOAD_DIRECTORY.resolve(item.getStorageKey()).normalize();
    if (!path.startsWith(UPLOAD_DIRECTORY) || !Files.isRegularFile(path))
      throw ApiException.notFound("Attachment not found.");
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + item.getName().replace("\"", "") + "\"")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(new FileSystemResource(path));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(
      @PathVariable Long taskId, @PathVariable Long id, HttpSession session) {
    Task task = findTask(taskId);
    if (task == null) throw ApiException.notFound("Task not found.");
    access.write(task.getProjectId(), session);
    TaskAttachment attachment = attachments.findById(id).orElse(null);
    if (attachment == null || !taskId.equals(attachment.getTaskId()))
      throw ApiException.notFound("Attachment not found.");
    storage.delete(task, attachment, session);
    return ResponseEntity.noContent().build();
  }

  private Task findTask(Long taskId) {
    return tasks.gettaskById(taskId).orElse(null);
  }

  public record AttachmentRequest(
      @jakarta.validation.constraints.Size(max = 255, message = "Link names can be at most 255 characters.")
          String name,
      @jakarta.validation.constraints.NotBlank(message = "Enter a link.")
          @jakarta.validation.constraints.Size(max = 2000, message = "Links can be at most 2000 characters.")
          String url) {}
}
