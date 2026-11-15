package com.sprintflowinsight.workspace.task;


import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

@Entity
public class Task {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank(message = "Task title is required.")
  private String title;

  private String description;
  private String status; // ToDo, In Progress, Review, Done

  private boolean isBlocked;
  private String blockReason;

  private Long projectId;
  private Long sprintId;
  private Long storyId;
  private String assigneeEmail;

  @jakarta.validation.constraints.Min(
      value = 0,
      message = "Story points must be between 0 and 1000.")
  @jakarta.validation.constraints.Max(
      value = 1000,
      message = "Story points must be between 0 and 1000.")
  private int storyPoints;

  @com.fasterxml.jackson.annotation.JsonProperty(
      access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
  private Instant createdAt;

  @com.fasterxml.jackson.annotation.JsonProperty(
      access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
  private Instant startedAt;

  @com.fasterxml.jackson.annotation.JsonProperty(
      access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
  private Instant completedAt;

  private Instant dueAt;
  @Version private Long version;

  // Constructors
  public Task() {}

  public Task(String title, String description, String status) {
    this.title = title;
    this.description = description;
    this.status = status;
    this.isBlocked = false;
    this.createdAt = Instant.now();
  }

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public boolean isBlocked() {
    return isBlocked;
  }

  public void setBlocked(boolean blocked) {
    isBlocked = blocked;
  }

  public String getBlockReason() {
    return blockReason;
  }

  public void setBlockReason(String blockReason) {
    this.blockReason = blockReason;
  }

  public Long getProjectId() {
    return projectId;
  }

  public void setProjectId(Long projectId) {
    this.projectId = projectId;
  }

  public Long getSprintId() {
    return sprintId;
  }

  public void setSprintId(Long sprintId) {
    this.sprintId = sprintId;
  }

  public Long getStoryId() {
    return storyId;
  }

  public void setStoryId(Long storyId) {
    this.storyId = storyId;
  }

  public String getAssigneeEmail() {
    return assigneeEmail;
  }

  public void setAssigneeEmail(String assigneeEmail) {
    this.assigneeEmail = assigneeEmail;
  }

  public int getStoryPoints() {
    return storyPoints;
  }

  public void setStoryPoints(int storyPoints) {
    this.storyPoints = storyPoints;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }

  public Instant getDueAt() {
    return dueAt;
  }

  public void setDueAt(Instant dueAt) {
    this.dueAt = dueAt;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }

  @com.fasterxml.jackson.annotation.JsonProperty(
      access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
  private java.time.Instant trashedAt;

  @com.fasterxml.jackson.annotation.JsonIgnore private String deletionBatchId;

  public String getDeletionBatchId() {
    return deletionBatchId;
  }

  public void setDeletionBatchId(String value) {
    deletionBatchId = value;
  }

  public java.time.Instant getTrashedAt() {
    return trashedAt;
  }

  public void setTrashedAt(java.time.Instant value) {
    trashedAt = value;
  }

  @jakarta.persistence.Convert(converter = LabelConverter.class)
  @jakarta.persistence.Column(columnDefinition = "text")
  @jakarta.validation.constraints.Size(max = 12)
  private java.util.List<
          @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 32)
          String>
      labels = new java.util.ArrayList<>();

  @jakarta.persistence.Convert(converter = ChecklistConverter.class)
  @jakarta.persistence.Column(columnDefinition = "text")
  @jakarta.validation.Valid
  @jakarta.validation.constraints.Size(max = 50)
  private java.util.List<ChecklistStep> checklist = new java.util.ArrayList<>();

  public java.util.List<String> getLabels() {
    return labels;
  }

  public void setLabels(java.util.List<String> value) {
    labels = value == null ? java.util.List.of() : value;
  }

  public java.util.List<ChecklistStep> getChecklist() {
    return checklist;
  }

  public void setChecklist(java.util.List<ChecklistStep> value) {
    checklist = value == null ? java.util.List.of() : value;
  }
}
