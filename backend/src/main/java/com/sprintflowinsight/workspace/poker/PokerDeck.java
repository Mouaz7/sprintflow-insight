package com.sprintflowinsight.workspace.poker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The card decks a planning poker session can use. A session copies its cards when it starts, so a
 * later change to a built-in deck never changes a running session.
 *
 * <p>The Fibonacci deck stops at 13 because stories accept 1, 2, 3, 5 or 8 points: 13 means "split
 * this story". {@link #ABSTAIN} cards ({@code ?} "unsure" and {@code ☕} "need a break") count as a
 * vote but not as an estimate.
 */
public enum PokerDeck {
  FIBONACCI(List.of("1", "2", "3", "5", "8", "13", "?", "☕")),
  TSHIRT(List.of("XS", "S", "M", "L", "XL", "?", "☕")),
  CUSTOM(List.of());

  /** Cards that take part without estimating. */
  public static final Set<String> ABSTAIN = Set.of("?", "☕");

  static final int MIN_CUSTOM_CARDS = 2;
  static final int MAX_CUSTOM_CARDS = 20;
  static final int MAX_CARD_LENGTH = 8;

  /** Letters, digits, symbols such as ☕, and {@code ? . + ½ -}; no whitespace or markup. */
  private static final Pattern CARD =
      Pattern.compile("[\\p{L}\\p{N}\\p{So}?.+\\-]{1," + MAX_CARD_LENGTH + "}");

  private final List<String> builtIn;

  PokerDeck(List<String> builtIn) {
    this.builtIn = builtIn;
  }

  /**
   * The playable cards in deck order.
   *
   * @param custom the requested cards; required for {@link #CUSTOM} and rejected otherwise
   * @throws IllegalArgumentException with a message safe to show the client
   */
  public List<String> cards(List<String> custom) {
    if (this != CUSTOM) {
      if (custom != null && !custom.isEmpty())
        throw new IllegalArgumentException("Only a custom deck takes its own cards.");
      return builtIn;
    }
    if (custom == null || custom.size() < MIN_CUSTOM_CARDS || custom.size() > MAX_CUSTOM_CARDS)
      throw new IllegalArgumentException(
          "A custom deck needs " + MIN_CUSTOM_CARDS + " to " + MAX_CUSTOM_CARDS + " cards.");
    List<String> cards = new ArrayList<>(custom.size());
    Set<String> seen = new HashSet<>();
    boolean estimates = false;
    for (String raw : custom) {
      String card = raw == null ? "" : raw.strip();
      if (!CARD.matcher(card).matches())
        throw new IllegalArgumentException(
            "Each card has 1 to "
                + MAX_CARD_LENGTH
                + " letters, digits or symbols, without spaces.");
      if (!seen.add(card)) throw new IllegalArgumentException("Cards in a deck must be unique.");
      estimates |= !ABSTAIN.contains(card);
      cards.add(card);
    }
    if (!estimates)
      throw new IllegalArgumentException("A custom deck needs at least one estimating card.");
    return List.copyOf(cards);
  }

  /** Whether {@code card} counts as an estimate (it is not an abstain card). */
  public static boolean estimates(String card) {
    return !ABSTAIN.contains(card);
  }
}
