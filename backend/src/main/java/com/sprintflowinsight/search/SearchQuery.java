package com.sprintflowinsight.search;

import com.sprintflowinsight.workspace.task.TaskKey;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A parsed search text. User input never reaches {@code to_tsquery} syntax directly: it is split
 * into words of letters and digits only, and each word becomes a quoted prefix term. A text that is
 * exactly a task key ({@code SF-123}) is also kept as a key for an exact lookup.
 *
 * @param terms distinct lower-case words, at most {@value #MAX_TERMS}, each at most {@value
 *     #MAX_TERM_LENGTH} code points
 */
public record SearchQuery(List<String> terms, Optional<TaskKey> taskKey) {
  public static final int MAX_TERMS = 8;
  public static final int MAX_TERM_LENGTH = 64;
  private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");
  private static final Pattern TERM = Pattern.compile("[\\p{L}\\p{N}]{1," + MAX_TERM_LENGTH + "}");

  public SearchQuery {
    terms = List.copyOf(terms);
    if (terms.size() > MAX_TERMS || !terms.stream().allMatch(term -> TERM.matcher(term).matches()))
      throw new IllegalArgumentException("Invalid search terms.");
  }

  public static SearchQuery parse(String text) {
    if (text == null) return new SearchQuery(List.of(), Optional.empty());
    String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    Set<String> terms = new LinkedHashSet<>();
    for (String word : SEPARATORS.split(normalized)) {
      if (word.isEmpty()) continue;
      terms.add(
          word.codePointCount(0, word.length()) > MAX_TERM_LENGTH ? truncate(word) : word);
      if (terms.size() == MAX_TERMS) break;
    }
    return new SearchQuery(new ArrayList<>(terms), TaskKey.parse(text));
  }

  /** Keeps the first {@value #MAX_TERM_LENGTH} code points, never splitting a surrogate pair. */
  private static String truncate(String word) {
    return word.substring(0, word.offsetByCodePoints(0, MAX_TERM_LENGTH));
  }

  public boolean isEmpty() {
    return terms.isEmpty() && taskKey.isEmpty();
  }

  /**
   * The {@code to_tsquery} argument: every word as a quoted prefix, all required, e.g. {@code
   * 'login':* & 'bug':*}. Words contain only letters and digits, so quoting cannot be escaped.
   */
  public String tsquery() {
    return terms.stream().map(term -> "'" + term + "':*").collect(Collectors.joining(" & "));
  }

  /** The words joined by spaces, compared with titles through trigram word similarity. */
  public String text() {
    return String.join(" ", terms);
  }
}
