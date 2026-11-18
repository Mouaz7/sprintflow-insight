package com.sprintflowinsight.search;

import java.util.List;

/**
 * One search result. Text is returned as plain segments; {@code match} marks the words that
 * matched, so clients highlight without interpreting markup.
 *
 * @param type {@code task}, {@code story}, {@code sprint}, {@code comment} or {@code project}
 * @param id id of the matched item (for a comment, the comment id)
 * @param taskId the task to open for tasks and comments, otherwise {@code null}
 * @param key task key such as {@code SF-12} for tasks and comments, otherwise {@code null}
 * @param title the item's title or name (for a comment, its task's title)
 * @param snippet a short extract of the body with matches marked; empty when there is no body
 * @param exactMatch {@code true} when the query was exactly this task's key
 */
public record SearchHit(
    String type,
    long id,
    long projectId,
    String projectKey,
    String projectName,
    Long taskId,
    String key,
    String title,
    List<Segment> titleSegments,
    List<Segment> snippet,
    boolean exactMatch) {

  /** A run of text that either matched the query or did not. */
  public record Segment(String text, boolean match) {}
}
