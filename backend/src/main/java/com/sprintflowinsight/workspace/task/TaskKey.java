package com.sprintflowinsight.workspace.task;

import com.sprintflowinsight.workspace.project.ProjectKeys;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A human task identifier such as {@code SF-123}: project key, hyphen, per-project number. */
public record TaskKey(String projectKey, int number) {
  private static final Pattern FORMAT =
      Pattern.compile("(" + ProjectKeys.REGEX + ")-([1-9][0-9]{0,8})");

  public TaskKey {
    if (!ProjectKeys.isValid(projectKey) || number < 1)
      throw new IllegalArgumentException("Invalid task key.");
  }

  /** Parses {@code SF-123}, case-insensitively and ignoring surrounding whitespace. */
  public static Optional<TaskKey> parse(String text) {
    if (text == null || text.length() > 32) return Optional.empty();
    Matcher match = FORMAT.matcher(text.trim().toUpperCase(Locale.ROOT));
    if (!match.matches()) return Optional.empty();
    return Optional.of(new TaskKey(match.group(1), Integer.parseInt(match.group(2))));
  }

  @Override
  public String toString() {
    return projectKey + "-" + number;
  }
}
