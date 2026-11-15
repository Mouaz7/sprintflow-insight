package com.sprintflowinsight.workspace.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "projects")
public class Project {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank(message = "Project name is required.")
  @Column(nullable = false, unique = true)
  private String name;

  private String description;

  /** Unique 2–10 letter key, e.g. {@code SF}; prefixes task identifiers such as SF-123. */
  @Column(name = "key", nullable = false, unique = true, length = ProjectKeys.MAX_LENGTH)
  private String projectKey;

  @Email(message = "Owner email must be valid.")
  @Column(nullable = false)
  private String ownerEmail;

  @Version private Long version;

  public Project() {}

  public Project(String name, String description, String ownerEmail) {
    this(name, description, ownerEmail, null);
  }

  public Project(String name, String description, String ownerEmail, String key) {
    this.projectKey = key;
    this.name = name;
    this.description = description;
    this.ownerEmail = ownerEmail;
  }

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

  /** Serialised as {@code key}; the attribute avoids the JPQL keyword KEY. */
  public String getKey() {
    return projectKey;
  }

  public void setKey(String key) {
    this.projectKey = key;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getOwnerEmail() {
    return ownerEmail;
  }

  public void setOwnerEmail(String ownerEmail) {
    this.ownerEmail = ownerEmail;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }

  @com.fasterxml.jackson.annotation.JsonProperty(
      access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
  private java.time.Instant archivedAt;

  @com.fasterxml.jackson.annotation.JsonProperty(
      access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
  private java.time.Instant trashedAt;

  public java.time.Instant getArchivedAt() {
    return archivedAt;
  }

  public void setArchivedAt(java.time.Instant value) {
    archivedAt = value;
  }

  public java.time.Instant getTrashedAt() {
    return trashedAt;
  }

  public void setTrashedAt(java.time.Instant value) {
    trashedAt = value;
  }
}
