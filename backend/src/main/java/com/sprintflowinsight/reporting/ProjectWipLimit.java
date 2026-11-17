package com.sprintflowinsight.reporting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

/** A WIP limit for one board column of one project (V19). */
@Entity
@Table(name = "project_wip_limit")
@IdClass(ProjectWipLimit.Key.class)
class ProjectWipLimit {
  @Id private Long projectId;
  @Id private String status;

  @Column(name = "wip_limit")
  private int limit;

  protected ProjectWipLimit() {}

  ProjectWipLimit(Long projectId, String status, int limit) {
    this.projectId = projectId;
    this.status = status;
    this.limit = limit;
  }

  Long getProjectId() {
    return projectId;
  }

  String getStatus() {
    return status;
  }

  int getLimit() {
    return limit;
  }

  /** Composite primary key. */
  static class Key implements Serializable {
    private Long projectId;
    private String status;

    protected Key() {}

    @Override
    public boolean equals(Object other) {
      return other instanceof Key key
          && Objects.equals(projectId, key.projectId)
          && Objects.equals(status, key.status);
    }

    @Override
    public int hashCode() {
      return Objects.hash(projectId, status);
    }
  }
}
