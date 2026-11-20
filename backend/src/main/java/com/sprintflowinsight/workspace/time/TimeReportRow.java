package com.sprintflowinsight.workspace.time;

/**
 * @param varianceMinutes logged minus estimated; {@code null} without an estimate
 * @param variancePercent the variance relative to the estimate; {@code null} without a positive
 *     estimate
 */
public record TimeReportRow(
    Long taskId,
    String key,
    String title,
    String status,
    String assigneeEmail,
    Integer estimateMinutes,
    long loggedMinutes,
    Long varianceMinutes,
    Integer variancePercent) {}
