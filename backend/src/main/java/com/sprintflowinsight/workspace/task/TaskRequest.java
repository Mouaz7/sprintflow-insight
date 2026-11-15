package com.sprintflowinsight.workspace.task;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * The fields a client may set on a task. Server-owned state (id, version, timestamps, recycle-bin
 * markers) cannot be mass-assigned because it is simply not part of the request.
 */
public record TaskRequest(
    @NotBlank(message = "Task title is required.")
        @Size(max = 255, message = "Task title can be at most 255 characters.")
        String title,
    @Size(max = 255, message = "Description can be at most 255 characters.") String description,
    String status,
    boolean blocked,
    @Size(max = 255, message = "Block reason can be at most 255 characters.") String blockReason,
    Long projectId,
    Long sprintId,
    Long storyId,
    @Size(max = 255) String assigneeEmail,
    @Min(value = 0, message = "Story points must be between 0 and 1000.")
        @Max(value = 1000, message = "Story points must be between 0 and 1000.")
        int storyPoints,
    Instant dueAt,
    @Size(max = 12, message = "A task can have at most 12 labels.")
        List<@NotBlank @Size(max = 32, message = "Labels can be at most 32 characters.") String>
            labels,
    @Size(max = 50, message = "A checklist can have at most 50 steps.")
        List<@Valid ChecklistStep> checklist) {

  /** A detached task carrying only the requested values, for validation and service updates. */
  public Task toEntity() {
    Task task = new Task(title.trim(), description, status);
    task.setBlocked(blocked);
    task.setBlockReason(blockReason);
    task.setProjectId(projectId);
    task.setSprintId(sprintId);
    task.setStoryId(storyId);
    task.setAssigneeEmail(assigneeEmail);
    task.setStoryPoints(storyPoints);
    task.setDueAt(dueAt);
    task.setLabels(labels == null ? List.of() : labels.stream().map(String::trim).toList());
    task.setChecklist(checklist == null ? List.of() : checklist);
    return task;
  }
}
