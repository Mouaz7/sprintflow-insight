package com.sprintflowinsight.reporting;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Revision of the whole limit collection, serialized by the project's row lock. */
@Entity
@Table(name = "project_wip_settings")
class ProjectWipSettings {
  @Id private Long projectId;
  private long version;

  protected ProjectWipSettings() {}

  ProjectWipSettings(Long projectId, long version) {
    this.projectId = projectId;
    this.version = version;
  }

  long getVersion() {
    return version;
  }
}
