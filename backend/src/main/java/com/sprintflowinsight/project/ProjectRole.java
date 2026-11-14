package com.sprintflowinsight.project;

import java.util.Locale;

/** Project roles from most to least privileged. Stored and serialised by {@link #label()}. */
public enum ProjectRole {
  OWNER("Owner"),
  ADMIN("Admin"),
  MEMBER("Member"),
  VIEWER("Viewer");

  private final String label;

  ProjectRole(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  public boolean canWrite() {
    return this != VIEWER;
  }

  public boolean canManage() {
    return this == OWNER || this == ADMIN;
  }

  /** Parses a stored label; unknown legacy values are treated as the least privileged role. */
  public static ProjectRole fromLabel(String label) {
    if (label == null) return VIEWER;
    return switch (label.trim().toLowerCase(Locale.ROOT)) {
      case "owner" -> OWNER;
      case "admin" -> ADMIN;
      case "member" -> MEMBER;
      default -> VIEWER;
    };
  }
}
