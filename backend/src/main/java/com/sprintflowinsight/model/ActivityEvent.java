package com.sprintflowinsight.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class ActivityEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long projectId;
  private String eventType;

  @jakarta.persistence.Column(length = 1000)
  private String summary;

  private String actorEmail;
  private Long actorUserId;
  private String entityType;
  private Long entityId;

  public Long getActorUserId() {
    return actorUserId;
  }

  public void setActorUserId(Long value) {
    actorUserId = value;
  }

  public String getEntityType() {
    return entityType;
  }

  public void setEntityType(String value) {
    entityType = value;
  }

  public Long getEntityId() {
    return entityId;
  }

  public void setEntityId(Long value) {
    entityId = value;
  }

  private Instant createdAt;

  public Long getId() {
    return id;
  }

  public Long getProjectId() {
    return projectId;
  }

  public void setProjectId(Long projectId) {
    this.projectId = projectId;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getActorEmail() {
    return actorEmail;
  }

  public void setActorEmail(String actorEmail) {
    this.actorEmail = actorEmail;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
