package com.sprintflowinsight.workspace.roadmap;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** {@code blockerTaskId} must be finished before {@code blockedTaskId} can be finished. */
@Entity
@Table(name = "task_dependency")
public class TaskDependency {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private Long projectId;

  @Column(nullable = false, updatable = false)
  private Long blockerTaskId;

  @Column(nullable = false, updatable = false)
  private Long blockedTaskId;

  @Column(updatable = false)
  private Long createdBy;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  protected TaskDependency() {}

  public TaskDependency(
      Long projectId, Long blockerTaskId, Long blockedTaskId, Long createdBy, Instant createdAt) {
    this.projectId = projectId;
    this.blockerTaskId = blockerTaskId;
    this.blockedTaskId = blockedTaskId;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public Long getProjectId() {
    return projectId;
  }

  public Long getBlockerTaskId() {
    return blockerTaskId;
  }

  public Long getBlockedTaskId() {
    return blockedTaskId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
