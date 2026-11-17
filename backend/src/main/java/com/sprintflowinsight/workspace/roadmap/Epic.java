package com.sprintflowinsight.workspace.roadmap;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;

/** A large piece of work that groups stories of one project on the roadmap. */
@Entity
@Table(name = "epic")
public class Epic {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private Long projectId;

  @Column(nullable = false)
  private String title;

  private String description;

  @Column(nullable = false)
  private String color;

  private LocalDate startDate;
  private LocalDate targetDate;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Version private Long version;

  protected Epic() {}

  public Epic(Long projectId, Instant createdAt) {
    this.projectId = projectId;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public Long getProjectId() {
    return projectId;
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

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalDate getTargetDate() {
    return targetDate;
  }

  public void setTargetDate(LocalDate targetDate) {
    this.targetDate = targetDate;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Long getVersion() {
    return version;
  }
}
