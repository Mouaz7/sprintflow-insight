package com.sprintflowinsight;

import static org.assertj.core.api.Assertions.*;

import com.sprintflowinsight.reporting.TaskExportFilter;
import com.sprintflowinsight.workspace.task.Task;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskExportFilterTest {
  @Test
  void combinesEveryBoardFilterAndRejectsEachNonmatchingCondition() {
    Task task = new Task("Fix export", "Searchable detail", "To Do");
    task.setBlocked(true);
    task.setLabels(List.of("Bug"));
    task.setAssigneeEmail("owner@example.com");
    Instant now = Instant.parse("2026-10-20T12:00:00Z");
    task.setDueAt(now.minusSeconds(1));
    assertThat(
            new TaskExportFilter(
                    "To Do", "backlog", "owner@example.com", "overdue", true, "Bug", "SEARCHABLE")
                .matches(task, now))
        .isTrue();
    for (TaskExportFilter filter :
        List.of(
            new TaskExportFilter("Done", null, null, null, null, null, null),
            new TaskExportFilter(null, null, "unassigned", null, null, null, null),
            new TaskExportFilter(null, null, null, "no-date", null, null, null),
            new TaskExportFilter(null, null, null, null, null, "Other", null),
            new TaskExportFilter(null, null, null, null, null, null, "Missing")))
      assertThat(filter.matches(task, now)).isFalse();
    task.setSprintId(7L);
    assertThat(
            new TaskExportFilter(null, "backlog", null, null, null, null, null).matches(task, now))
        .isFalse();
    task.setStatus("Done");
    assertThat(
            new TaskExportFilter(null, null, null, "overdue", null, null, null).matches(task, now))
        .isFalse();
  }
}
