package com.sprintflowinsight.workspace.time;

import java.time.Instant;
import java.time.LocalDate;

/**
 * @param userEmail {@code null} for a former member or a deleted account
 * @param minutes {@code null} while the timer runs
 */
public record TimeEntryResponse(
    Long id,
    Long taskId,
    String taskKey,
    String taskTitle,
    Long userId,
    String userEmail,
    LocalDate spentOn,
    Integer minutes,
    boolean running,
    Instant startedAt,
    String note,
    Long version) {

  static TimeEntryResponse of(TimeEntryRow row) {
    TimeEntry e = row.entry();
    String key =
        row.projectKey() == null || row.taskNumber() == null
            ? null
            : row.projectKey() + "-" + row.taskNumber();
    return new TimeEntryResponse(
        e.getId(),
        e.getTaskId(),
        key,
        row.taskTitle(),
        e.getUserId(),
        row.userEmail(),
        e.getSpentOn(),
        e.getMinutes(),
        e.running(),
        e.getStartedAt(),
        e.getNote(),
        e.getVersion());
  }
}
