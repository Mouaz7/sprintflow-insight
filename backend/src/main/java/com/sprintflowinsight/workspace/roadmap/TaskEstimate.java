package com.sprintflowinsight.workspace.roadmap;

import com.sprintflowinsight.workspace.roadmap.schedule.ThreePointEstimate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

/** A task's three-point estimate in working days. */
@Entity
@Table(name = "task_estimate")
public class TaskEstimate {
  @Id private Long taskId;

  @Version private Long version;

  @Column(nullable = false)
  private boolean active = true;

  @Column(nullable = false, updatable = false)
  private Long projectId;

  @Column(name = "optimistic_days", nullable = false, precision = 6, scale = 2)
  private BigDecimal optimistic;

  @Column(name = "most_likely_days", nullable = false, precision = 6, scale = 2)
  private BigDecimal mostLikely;

  @Column(name = "pessimistic_days", nullable = false, precision = 6, scale = 2)
  private BigDecimal pessimistic;

  @Column(nullable = false)
  private Instant updatedAt;

  protected TaskEstimate() {}

  public TaskEstimate(Long taskId, Long projectId) {
    this.taskId = taskId;
    this.projectId = projectId;
  }

  public void set(
      BigDecimal optimistic, BigDecimal mostLikely, BigDecimal pessimistic, Instant at) {
    this.optimistic = optimistic;
    this.mostLikely = mostLikely;
    this.pessimistic = pessimistic;
    this.updatedAt = at;
    this.active = true;
  }

  public boolean isActive() {
    return active;
  }

  public void clear(Instant at) {
    active = false;
    updatedAt = at;
  }

  public Long getTaskId() {
    return taskId;
  }

  public Long getVersion() {
    return version;
  }

  public BigDecimal getOptimistic() {
    return optimistic;
  }

  public BigDecimal getMostLikely() {
    return mostLikely;
  }

  public BigDecimal getPessimistic() {
    return pessimistic;
  }

  public ThreePointEstimate toEstimate() {
    return new ThreePointEstimate(
        optimistic.doubleValue(), mostLikely.doubleValue(), pessimistic.doubleValue());
  }
}
