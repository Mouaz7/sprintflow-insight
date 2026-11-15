package com.sprintflowinsight.workspace.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TaskKeyTest {
  @Test
  void parsesAndFormatsIdentifiers() {
    assertThat(TaskKey.parse("SF-123")).contains(new TaskKey("SF", 123));
    assertThat(TaskKey.parse(" sf-7 ")).contains(new TaskKey("SF", 7));
    assertThat(TaskKey.parse("ABCDEFGHIJ-999999999")).isPresent();
    assertThat(new TaskKey("SF", 42)).hasToString("SF-42");
  }

  @Test
  void rejectsEverythingElse() {
    for (String bad :
        new String[] {
          null, "", "SF", "SF-", "SF-0", "SF-01", "S-1", "SF1-2", "SF_1", "SF-1-2", "SF--1",
          "SF-1000000000", "ABCDEFGHIJK-1", "SF-١", "x".repeat(40)
        })
      assertThat(TaskKey.parse(bad)).as(String.valueOf(bad)).isEmpty();
    assertThatThrownBy(() -> new TaskKey("sf", 1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TaskKey("SF", 0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void exposesTheIdentifierOnTheTask() {
    Task task = new Task("Title", null, "To Do");
    assertThat(task.getKey()).isNull();
    task.setNumber(5);
    assertThat(task.getKey()).isNull();
    task.setProjectKey("SF");
    assertThat(task.getKey()).isEqualTo("SF-5");
    assertThat(task.getNumber()).isEqualTo(5);
    assertThat(task.getProjectKey()).isEqualTo("SF");
  }
}
