package com.sprintflowinsight.workspace.project;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Project keys such as {@code SF}: 2–10 upper-case ASCII letters, unique across all projects. The
 * same derivation and collision rule backfilled existing projects in migration V15.
 */
public final class ProjectKeys {
  public static final int MIN_LENGTH = 2;
  public static final int MAX_LENGTH = 10;
  public static final String REGEX = "[A-Z]{2,10}";
  public static final String FALLBACK = "PRJ";
  private static final Pattern VALID = Pattern.compile(REGEX);
  private static final Pattern MARKS = Pattern.compile("\\p{M}+");

  private ProjectKeys() {}

  public static boolean isValid(String key) {
    return key != null && VALID.matcher(key).matches();
  }

  /** Trims and upper-cases client input; validity is checked separately. */
  public static String normalize(String key) {
    return key == null ? null : key.trim().toUpperCase(Locale.ROOT);
  }

  /**
   * A readable key from a project name: the initials of up to four words ("Sprint Flow" → SF), or
   * the first four letters of a single word ("Apollo" → APOL). Accents are folded (Å → A).
   */
  public static String derive(String name) {
    List<String> words = words(name);
    StringBuilder key = new StringBuilder();
    if (words.size() >= 2) {
      for (int index = 0; index < Math.min(words.size(), 4); index++)
        key.append(words.get(index).charAt(0));
    } else if (words.size() == 1) {
      String word = words.getFirst();
      key.append(word, 0, Math.min(word.length(), 4));
    }
    return key.length() >= MIN_LENGTH ? key.toString() : FALLBACK;
  }

  /**
   * {@code base} itself when free, otherwise {@code base} shortened as needed and followed by the
   * first free suffix A, B, …, Z, AA, AB, …
   */
  public static String unique(String base, Predicate<String> taken) {
    if (!isValid(base)) throw new IllegalArgumentException("Invalid project key base.");
    String candidate = base;
    for (int attempt = 1; taken.test(candidate); attempt++) {
      String suffix = suffix(attempt);
      candidate = base.substring(0, Math.min(base.length(), MAX_LENGTH - suffix.length())) + suffix;
    }
    return candidate;
  }

  /** Bijective base-26: 1 → A, 26 → Z, 27 → AA. */
  static String suffix(int number) {
    if (number < 1) throw new IllegalArgumentException("Suffix numbers start at 1.");
    StringBuilder suffix = new StringBuilder();
    for (int remaining = number; remaining > 0; remaining = (remaining - 1) / 26)
      suffix.insert(0, (char) ('A' + (remaining - 1) % 26));
    return suffix.toString();
  }

  private static List<String> words(String name) {
    if (name == null) return List.of();
    String folded =
        MARKS
            .matcher(Normalizer.normalize(name, Normalizer.Form.NFD))
            .replaceAll("")
            .toUpperCase(Locale.ROOT)
            .replace('Æ', 'A')
            .replace('Ø', 'O');
    List<String> words = new ArrayList<>();
    for (String word : folded.split("[^A-Z]+")) if (!word.isEmpty()) words.add(word);
    return words;
  }
}
