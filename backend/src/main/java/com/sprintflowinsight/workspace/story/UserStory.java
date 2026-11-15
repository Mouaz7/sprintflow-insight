package com.sprintflowinsight.workspace.story;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@Entity
@Table(name = "user_stories")
public class UserStory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank(message = "Story title is required.")
  @Column(nullable = false)
  private String title;

  private String description;

  @NotBlank(message = "Acceptance criteria are required.")
  @Column(nullable = false)
  private String acceptanceCriteria; // متوافقة مع متطلبات US13

  @Min(value = 0, message = "Story points cannot be negative.")
  @Max(value = 100, message = "Story points cannot exceed 100.")
  private int storyPoints; // النقاط الخاصة بالقصة (SP)

  @Min(value = 1, message = "Priority must be between 1 and 3.")
  @Max(value = 3, message = "Priority must be between 1 and 3.")
  private int priorityId; // أولوية القصة

  @Min(value = 1, message = "Business value must be between 1 and 5.")
  @Max(value = 5, message = "Business value must be between 1 and 5.")
  private Integer businessValue;

  @NotNull(message = "Project is required.")
  @Column(nullable = false)
  private Long projectId; // لربط القصة بالمشروع وضمان عزل البيانات (US12)

  private Long sprintId;

  @com.fasterxml.jackson.annotation.JsonProperty(
      access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
  private long backlogPosition;

  public long getBacklogPosition() {
    return backlogPosition;
  }

  public void setBacklogPosition(long value) {
    backlogPosition = value;
  }

  @com.fasterxml.jackson.annotation.JsonProperty(
      access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
  private Instant createdAt;

  @Version private Long version;

  // Constructors
  public UserStory() {}

  public UserStory(
      String title,
      String description,
      String acceptanceCriteria,
      int storyPoints,
      int priorityId,
      Long projectId) {
    this.title = title;
    this.description = description;
    this.acceptanceCriteria = acceptanceCriteria;
    this.storyPoints = storyPoints;
    this.priorityId = priorityId;
    this.projectId = projectId;
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

  public String getAcceptanceCriteria() {
    return acceptanceCriteria;
  }

  public void setAcceptanceCriteria(String acceptanceCriteria) {
    this.acceptanceCriteria = acceptanceCriteria;
  }

  public int getStoryPoints() {
    return storyPoints;
  }

  public void setStoryPoints(int storyPoints) {
    this.storyPoints = storyPoints;
  }

  public int getPriorityId() {
    return priorityId;
  }

  public void setPriorityId(int priorityId) {
    this.priorityId = priorityId;
  }

  public Integer getBusinessValue() {
    return businessValue;
  }

  public void setBusinessValue(Integer businessValue) {
    this.businessValue = businessValue;
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

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
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
}
