package com.sprintflowinsight.risk;

/**
 * The features of the risk model, in vector order. None of them identifies a person: assignee,
 * reporter and commenter identities are intentionally absent (fairness principle).
 */
public enum RiskFeature {
  /** Hours in the current column divided by that column's historical 85th percentile. */
  AGE_IN_COLUMN_VS_P85,
  /** Number of times the task has been marked blocked. */
  TIMES_BLOCKED,
  /** Number of assignee changes (only the count, never who). */
  REASSIGNMENTS,
  /** Comments written on the task during the last seven days. */
  COMMENTS_LAST_7_DAYS,
  /** Story points; unestimated tasks use 0. */
  STORY_POINTS,
  /** Length of the longest chain of unfinished tasks this task depends on. */
  DEPENDENCY_DEPTH,
  /** Calendar days until the sprint ends (0 when the end has passed). */
  REMAINING_SPRINT_DAYS,
  /** Number of tasks currently in the task's column, including the task. */
  COLUMN_WIP;

  /** Number of features, the length of {@link RiskFeatures#toVector()}. */
  public static final int COUNT = values().length;
}
