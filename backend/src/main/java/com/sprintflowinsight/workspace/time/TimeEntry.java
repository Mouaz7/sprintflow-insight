package com.sprintflowinsight.workspace.time;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;

/** Time spent on a task: a manual entry, a stopped timer, or (minutes null) a running timer. */
@Entity
@Table(name = "time_entry")
public class TimeEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private Long projectId;

  @Column(nullable = false, updatable = false)
  private Long taskId;

  /** {@code null} after the account was deleted; the hours stay with the project. */
  @Column(updatable = false)
  private Long userId;

  @Column(nullable = false)
  private LocalDate spentOn;

  private Integer minutes;

  private Instant startedAt;

  @Column(length = 500)
  private String note;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Version private Long version;

  public boolean running() {
    return minutes == null;
  }

  public Long getId() {
    return id;
  }

  public Long getProjectId() {
    return projectId;
  }

  public void setProjectId(Long projectId) {
    this.projectId = projectId;
  }

  public Long getTaskId() {
    return taskId;
  }

  public void setTaskId(Long taskId) {
    this.taskId = taskId;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public LocalDate getSpentOn() {
    return spentOn;
  }

  public void setSpentOn(LocalDate spentOn) {
    this.spentOn = spentOn;
  }

  public Integer getMinutes() {
    return minutes;
  }

  public void setMinutes(Integer minutes) {
    this.minutes = minutes;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
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
}
