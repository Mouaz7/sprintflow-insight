package com.sprintflowinsight.planning.optimizer;

/** A planner's manual decision about one story. */
public enum Lock {
  /** The solver decides. */
  NONE,
  /** The story must be in the plan (together with any unfinished predecessors). */
  LOCKED_IN,
  /** The story must stay out of the plan, and so must every story that depends on it. */
  LOCKED_OUT
}
