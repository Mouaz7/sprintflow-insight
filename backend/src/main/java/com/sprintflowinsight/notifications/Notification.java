package com.sprintflowinsight.notifications;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class Notification {
  private java.util.UUID sourceEventId;

  @com.fasterxml.jackson.annotation.JsonIgnore
  public java.util.UUID getSourceEventId() {
    return sourceEventId;
  }

  public void setSourceEventId(java.util.UUID id) {
    this.sourceEventId = id;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long projectId;
  private String recipientEmail;

  @jakarta.persistence.Column(length = 1000)
  private String message;

  private boolean isRead;
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

  public String getRecipientEmail() {
    return recipientEmail;
  }

  public void setRecipientEmail(String recipientEmail) {
    this.recipientEmail = recipientEmail;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public boolean isRead() {
    return isRead;
  }

  public void setRead(boolean read) {
    isRead = read;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
