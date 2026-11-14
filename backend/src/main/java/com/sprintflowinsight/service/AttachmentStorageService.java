package com.sprintflowinsight.service;

import com.sprintflowinsight.model.Task;
import com.sprintflowinsight.model.TaskAttachment;
import com.sprintflowinsight.project.ProjectAccessService;
import com.sprintflowinsight.repository.TaskAttachmentRepository;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AttachmentStorageService {
  private final TaskAttachmentRepository attachments;
  private final FileDeletionService deletions;
  private final ProjectMutationService mutations;
  private final ProjectAccessService access;
  private final com.sprintflowinsight.repository.TaskRepository tasks;
  private final Path directory = Path.of("data", "attachments").toAbsolutePath().normalize();

  public AttachmentStorageService(
      TaskAttachmentRepository attachments,
      FileDeletionService deletions,
      ProjectMutationService mutations,
      ProjectAccessService access,
      com.sprintflowinsight.repository.TaskRepository tasks) {
    this.tasks = tasks;

    this.attachments = attachments;
    this.deletions = deletions;
    this.mutations = mutations;
    this.access = access;
  }

  @Transactional(rollbackFor = IOException.class)
  public TaskAttachment store(
      Task task, byte[] bytes, String extension, String name, HttpSession session)
      throws IOException {
    access.write(task.getProjectId(), session);
    mutations.lock(task.getProjectId());
    if (tasks.findById(task.getId()).filter(current -> current.getTrashedAt() == null).isEmpty())
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.NOT_FOUND, "Task not found.");
    Files.createDirectories(directory);
    Path staged = Files.createTempFile(directory, ".upload-", ".tmp");
    String key = UUID.randomUUID() + extension;
    Path destination = directory.resolve(key);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            try {
              Files.deleteIfExists(staged);
              if (status != STATUS_COMMITTED) Files.deleteIfExists(destination);
            } catch (IOException error) {
              /* A failed rollback cleanup remains visible to operators. */
              org.slf4j.LoggerFactory.getLogger(AttachmentStorageService.class)
                  .warn("Temporary attachment cleanup failed.");
            }
          }
        });
    Files.write(staged, bytes);
    Files.move(staged, destination, StandardCopyOption.ATOMIC_MOVE);
    TaskAttachment item = new TaskAttachment();
    item.setTaskId(task.getId());
    item.setProjectId(task.getProjectId());
    item.setName(name);
    item.setStorageKey(key);
    item.setCreatedAt(Instant.now());
    attachments.save(item);
    item.setUrl("/api/tasks/" + task.getId() + "/attachments/" + item.getId() + "/file");
    return item;
  }

  @Transactional
  public void delete(Task task, TaskAttachment attachment, HttpSession session) {
    access.write(task.getProjectId(), session);
    mutations.lock(task.getProjectId());
    if (attachment.getStorageKey() != null)
      deletions.enqueue(java.util.List.of(attachment.getStorageKey()));
    attachments.delete(attachment);
  }
}
