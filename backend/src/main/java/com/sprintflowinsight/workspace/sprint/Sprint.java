package com.sprintflowinsight.workspace.sprint;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalDate;

@Entity
public class Sprint {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank(message = "Sprint name is required.")
  private String name;

  private String goal;
  private String status; // Planned, Active, Completed, Cancelled (US18)

  @Min(value = 0, message = "Story points cannot be negative.")
  private int totalStoryPoints;

  private Long projectId;

  @com.fasterxml.jackson.annotation.JsonProperty(
      access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
  private Instant createdAt;

  @com.fasterxml.jackson.annotation.JsonProperty(
      access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
  private Instant completedAt;

  private LocalDate startDate;
  private LocalDate endDate;
  @Version private Long version;

  // Constructors
  public Sprint() {}

  public Sprint(String name, String goal, String status, int totalStoryPoints) {
    this.name = name;
    this.goal = goal;
    this.status = status;
    this.totalStoryPoints = totalStoryPoints;
  }

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getGoal() {
    return goal;
  }

  public void setGoal(String goal) {
    this.goal = goal;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public int getTotalStoryPoints() {
    return totalStoryPoints;
  }

  public void setTotalStoryPoints(int totalStoryPoints) {
    this.totalStoryPoints = totalStoryPoints;
  }

  public Long getProjectId() {
    return projectId;
  }

  public void setProjectId(Long projectId) {
    this.projectId = projectId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
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
