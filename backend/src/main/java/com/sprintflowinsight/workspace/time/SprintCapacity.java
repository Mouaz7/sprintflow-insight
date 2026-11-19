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
import java.util.List;

/** What one member can give one sprint. See {@link CapacityMath} for how it is evaluated. */
@Entity
@Table(name = "sprint_capacity")
public class SprintCapacity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private Long projectId;

  @Column(nullable = false, updatable = false)
  private Long sprintId;

  @Column(nullable = false, updatable = false)
  private Long userId;

  @Column(nullable = false)
  private int minutesPerDay;

  @Column(nullable = false)
  private int availabilityPercent;

  private Integer pointCapacity;

  /** A PostgreSQL {@code date[]}: one row per member keeps the capacity view a single query. */
  @Column(nullable = false)
  private LocalDate[] daysOff = new LocalDate[0];

  @Column(nullable = false)
  private Instant updatedAt;

  @Version private Long version;

  public Long getId() {
    return id;
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

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public int getMinutesPerDay() {
    return minutesPerDay;
  }

  public void setMinutesPerDay(int minutesPerDay) {
    this.minutesPerDay = minutesPerDay;
  }

  public int getAvailabilityPercent() {
    return availabilityPercent;
  }

  public void setAvailabilityPercent(int availabilityPercent) {
    this.availabilityPercent = availabilityPercent;
  }

  public Integer getPointCapacity() {
    return pointCapacity;
  }

  public void setPointCapacity(Integer pointCapacity) {
    this.pointCapacity = pointCapacity;
  }

  public List<LocalDate> getDaysOff() {
    return daysOff == null ? List.of() : List.of(daysOff);
  }

  public void setDaysOff(List<LocalDate> days) {
    this.daysOff = days.stream().distinct().sorted().toArray(LocalDate[]::new);
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Long getVersion() {
    return version;
  }
}
