package com.sprintflowinsight.common;

import java.util.regex.Pattern;

/**
 * Removes personal data from text before it reaches logs or trace attributes. Email addresses are
 * the personal data SprintFlow handles in URLs such as member routes. Request error logs use {@link
 * #path(String)} to drop the query and replace email addresses with a fixed marker.
 */
public final class LogRedaction {
  /** Replacement for an email address or an encoded email path segment. */
  public static final String EMAIL = "[email]";

  // Local part, then "@" or its percent-encoding, then a host name. Deliberately broad: a false
  // positive (for example a Java identity hash "Type@1a2b") only loses noise, a miss leaks data.
  private static final Pattern ADDRESS =
      Pattern.compile("[\\p{L}\\p{N}._%+'-]+(?:@|%40)[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)*");

  private LogRedaction() {}

  /** The text with every email address replaced by {@link #EMAIL}; {@code null} stays null. */
  public static String redact(String text) {
    if (text == null || (text.indexOf('@') < 0 && !text.contains("%40"))) return text;
    return ADDRESS.matcher(text).replaceAll(EMAIL);
  }

  /** A request path that is safe to log: the query string is dropped and emails are redacted. */
  public static String path(String uri) {
    if (uri == null) return null;
    int query = uri.indexOf('?');
    return redact(query < 0 ? uri : uri.substring(0, query));
  }
}
