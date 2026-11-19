package com.sprintflowinsight.workspace.collaboration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds {@code @handle} mentions in free text with a single left-to-right scan (no regular
 * expressions, so no backtracking on hostile input).
 *
 * <p>Grammar:
 *
 * <ul>
 *   <li>A mention starts with {@code @} at the start of the text or after a character that cannot
 *       be part of a handle and is not {@code @}. An email address in running text ({@code
 *       anna@example.com}) is therefore not a mention, while {@code (@anna)} is.
 *   <li>The handle is the longest run of handle characters: letters and digits (ASCII) and {@code
 *       . _ % + -}, optionally followed by one {@code @} and a domain of letters, digits, {@code .}
 *       and {@code -}. So both {@code @anna} and {@code @anna@example.com} are handles.
 *   <li>Trailing {@code .} and {@code -} are punctuation, not part of the handle ({@code "Thanks
 *       @anna."}).
 *   <li>Text inside a closed pair of backticks is code and never contains mentions. An unpaired
 *       backtick is literal.
 * </ul>
 *
 * <p>Handles are returned lower-cased, distinct, in order of first appearance, and at most {@link
 * #MAX_MENTIONS}; handles longer than {@link #MAX_HANDLE_LENGTH} are ignored. Resolving a handle to
 * an account is {@link MentionResolver}'s job.
 */
public final class MentionParser {
  public static final int MAX_MENTIONS = 20;
  public static final int MAX_HANDLE_LENGTH = 254;

  private MentionParser() {}

  public static List<String> parse(String text) {
    if (text == null || text.isEmpty()) return List.of();
    Set<String> handles = new LinkedHashSet<>();
    int length = text.length();
    int index = 0;
    while (index < length && handles.size() < MAX_MENTIONS) {
      char current = text.charAt(index);
      if (current == '`') {
        int close = text.indexOf('`', index + 1);
        index = close < 0 ? index + 1 : close + 1;
        continue;
      }
      if (current != '@' || !boundaryBefore(text, index)) {
        index++;
        continue;
      }
      int end = scanHandle(text, index + 1);
      String handle = trimPunctuation(text.substring(index + 1, end));
      if (!handle.isEmpty() && handle.length() <= MAX_HANDLE_LENGTH)
        handles.add(handle.toLowerCase(Locale.ROOT));
      index = Math.max(end, index + 1);
    }
    return List.copyOf(new ArrayList<>(handles));
  }

  /** True when {@code character} may appear in the local part of a handle. */
  static boolean localCharacter(char character) {
    return asciiLetterOrDigit(character)
        || character == '.'
        || character == '_'
        || character == '%'
        || character == '+'
        || character == '-';
  }

  static boolean domainCharacter(char character) {
    return asciiLetterOrDigit(character) || character == '.' || character == '-';
  }

  private static boolean asciiLetterOrDigit(char character) {
    return (character >= 'a' && character <= 'z')
        || (character >= 'A' && character <= 'Z')
        || (character >= '0' && character <= '9');
  }

  private static boolean boundaryBefore(String text, int at) {
    if (at == 0) return true;
    char previous = text.charAt(at - 1);
    return previous != '@' && !localCharacter(previous);
  }

  /** End (exclusive) of the handle that starts at {@code start}. */
  private static int scanHandle(String text, int start) {
    int length = text.length();
    int index = start;
    while (index < length && localCharacter(text.charAt(index))) index++;
    if (index == start || index >= length - 1 || text.charAt(index) != '@') return index;
    int domainStart = index + 1;
    int domainEnd = domainStart;
    while (domainEnd < length && domainCharacter(text.charAt(domainEnd))) domainEnd++;
    String domain = trimPunctuation(text.substring(domainStart, domainEnd));
    // "@anna@" or "@anna@." is a handle followed by stray punctuation, not an address.
    return domain.isEmpty() ? index : domainEnd;
  }

  private static String trimPunctuation(String handle) {
    int end = handle.length();
    while (end > 0 && (handle.charAt(end - 1) == '.' || handle.charAt(end - 1) == '-')) end--;
    return handle.substring(0, end);
  }
}
