package com.sprintflowinsight.reporting;

/**
 * The WIP limit of one column.
 *
 * @param limit {@code null} when the column has no limit
 */
public record WipLimitResponse(String status, Integer limit) {}
