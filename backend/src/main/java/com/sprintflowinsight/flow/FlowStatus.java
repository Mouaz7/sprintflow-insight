package com.sprintflowinsight.flow;

import java.util.List;
import java.util.Set;

/** The board columns, in flow order. */
public final class FlowStatus {
  public static final String TO_DO = "To Do";
  public static final String IN_PROGRESS = "In Progress";
  public static final String REVIEW = "Review";
  public static final String DONE = "Done";

  /** All columns, left to right. */
  public static final List<String> ALL = List.of(TO_DO, IN_PROGRESS, REVIEW, DONE);

  /** Columns where work is in progress: the "system" for cycle time and Little's law. */
  public static final Set<String> ACTIVE = Set.of(IN_PROGRESS, REVIEW);

  /** Columns that can carry a WIP limit. */
  public static final List<String> LIMITABLE = List.of(TO_DO, IN_PROGRESS, REVIEW);

  private FlowStatus() {}

  public static boolean active(String status) {
    return ACTIVE.contains(status);
  }
}
