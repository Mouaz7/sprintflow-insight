package com.sprintflowinsight.crdt;

/** Each collaboration room belongs to a live, project-scoped resource. */
public enum CrdtTarget {
  TASK("task_id", 255),
  RETROSPECTIVE("retrospective_id", 1000);

  final String column;
  final int maxLength;

  CrdtTarget(String column, int maxLength) {
    this.column = column;
    this.maxLength = maxLength;
  }
}
