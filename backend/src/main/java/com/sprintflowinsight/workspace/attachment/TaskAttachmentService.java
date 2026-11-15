package com.sprintflowinsight.workspace.attachment;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskService;
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
import org.springframework.web.multipart.MultipartFile;

@org.springframework.stereotype.Service
public class TaskAttachmentService {
  private static final Path UPLOAD_DIRECTORY =
      Path.of("data", "attachments").toAbsolutePath().normalize();
  private final com.sprintflowinsight.workspace.attachment.UploadValidator validator;
  private final com.sprintflowinsight.workspace.attachment.AttachmentStorageService storage;
  private final TaskService tasks;
  private final TaskAttachmentRepository attachments;
  private final ProjectAccessService access;

  public TaskAttachmentService(
      TaskService tasks,
      TaskAttachmentRepository attachments,
      ProjectAccessService access,
      com.sprintflowinsight.workspace.attachment.UploadValidator validator,
      com.sprintflowinsight.workspace.attachment.AttachmentStorageService storage) {
    this.validator = validator;
    this.storage = storage;

    this.tasks = tasks;
    this.attachments = attachments;
    this.access = access;
  }

  public ResponseEntity<?> list(Long taskId, HttpSession session) {
    Task task = findTask(taskId);
    if (task == null) throw ApiException.notFound("Task not found.");
    access.read(task.getProjectId(), session);
    return ResponseEntity.ok(attachments.findByTaskIdOrderByCreatedAtAsc(taskId));
  }

  public ResponseEntity<?> addLink(Long taskId, AttachmentRequest request, HttpSession session) {
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

  public ResponseEntity<?> upload(Long taskId, MultipartFile file, HttpSession session) {
    Task task = findTask(taskId);
    if (task == null) throw ApiException.notFound("Task not found.");
    access.write(task.getProjectId(), session);
    if (file.isEmpty()
        || file.getSize() > com.sprintflowinsight.workspace.attachment.UploadValidator.MAX_BYTES)
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
          com.sprintflowinsight.common.error.ErrorCode.INTERNAL_ERROR,
          "Could not store the attachment.");
    }
  }

  public ResponseEntity<Resource> file(Long taskId, Long id, HttpSession session) {
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

  public ResponseEntity<?> delete(Long taskId, Long id, HttpSession session) {
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
}
