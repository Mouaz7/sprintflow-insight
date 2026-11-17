package com.sprintflowinsight.workspace.roadmap.schedule;

/**
 * A precedence: {@code from} must be finished before {@code to} can be finished.
 *
 * @param from the blocking task
 * @param to the blocked task
 */
public record Edge(long from, long to) {}
