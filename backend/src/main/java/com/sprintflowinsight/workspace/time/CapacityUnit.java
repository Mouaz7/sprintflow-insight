package com.sprintflowinsight.workspace.time;

/** The resource the sprint optimizer should plan against. */
public enum CapacityUnit {
  /** Whole available hours per member, rounded down. */
  HOURS,
  /** Effective story points per member (point capacity scaled by days off and availability). */
  POINTS
}
