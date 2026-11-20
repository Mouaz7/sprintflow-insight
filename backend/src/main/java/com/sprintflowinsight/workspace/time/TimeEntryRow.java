package com.sprintflowinsight.workspace.time;

/** A time entry with the display fields read in the same query. */
public record TimeEntryRow(
    TimeEntry entry, String userEmail, String taskTitle, String projectKey, Integer taskNumber) {}
