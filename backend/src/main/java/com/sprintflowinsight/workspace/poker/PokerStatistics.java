package com.sprintflowinsight.workspace.poker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What a revealed round says about agreement. All order-based figures use the position of a card in
 * its deck, so they work for numeric, T-shirt and custom decks alike; {@link #mean} exists only
 * when every estimate is a number. Abstain cards ({@link PokerDeck#ABSTAIN}) are counted but do not
 * estimate.
 *
 * @param votes every vote in the round, abstentions included
 * @param abstentions votes with an abstain card
 * @param distribution how many voted each card, in deck order, only cards that were played
 * @param min lowest estimate in deck order, {@code null} without estimates
 * @param max highest estimate in deck order, {@code null} without estimates
 * @param median lower median of the estimates in deck order, {@code null} without estimates
 * @param modes the most played estimates in deck order (several on a tie)
 * @param consensus at least one estimate and all estimates are the same card
 * @param agreement share of estimates on the most played card, in [0, 1]; 0 without estimates
 * @param mean arithmetic mean when every estimate is numeric, otherwise {@code null}
 * @param spread deck positions between the lowest and highest estimate; 0 on consensus
 * @param suggestion the card the team is offered: the median (the agreed card on consensus)
 * @param suggestedStoryPoints the suggestion as story points when it is 1, 2, 3, 5 or 8
 * @param revoteRecommended no estimates, or estimates further apart than neighbouring cards
 */
public record PokerStatistics(
    int votes,
    int abstentions,
    List<CardCount> distribution,
    String min,
    String max,
    String median,
    List<String> modes,
    boolean consensus,
    double agreement,
    Double mean,
    int spread,
    String suggestion,
    Integer suggestedStoryPoints,
    boolean revoteRecommended) {

  /** Story points a story accepts (the {@code story_points_allowed} constraint). */
  public static final Set<Integer> STORY_POINTS = Set.of(1, 2, 3, 5, 8);

  private static final Pattern NUMBER = Pattern.compile("[0-9]{1,6}(\\.[0-9]{1,2})?|½");

  public record CardCount(String card, int count) {}

  /**
   * Statistics for one round.
   *
   * @param deck the session's cards in deck order
   * @param cards the played cards, one per voter, in any order
   * @throws IllegalArgumentException when a played card is not in the deck
   */
  public static PokerStatistics of(List<String> deck, Collection<String> cards) {
    Map<String, Integer> position = new HashMap<>();
    for (int index = 0; index < deck.size(); index++) position.putIfAbsent(deck.get(index), index);
    int[] counts = new int[deck.size()];
    int abstentions = 0;
    for (String card : cards) {
      Integer index = position.get(card);
      if (index == null) throw new IllegalArgumentException("A vote is not a card of this deck.");
      counts[index]++;
      if (!PokerDeck.estimates(card)) abstentions++;
    }

    List<CardCount> distribution = new ArrayList<>();
    for (int index = 0; index < counts.length; index++)
      if (counts[index] > 0) distribution.add(new CardCount(deck.get(index), counts[index]));

    // Estimate positions in ascending deck order.
    int estimateCount = cards.size() - abstentions;
    int[] sorted = new int[estimateCount];
    int next = 0;
    int highest = 0;
    for (int index = 0; index < counts.length; index++) {
      if (!PokerDeck.estimates(deck.get(index))) continue;
      for (int copy = 0; copy < counts[index]; copy++) sorted[next++] = index;
      highest = Math.max(highest, counts[index]);
    }

    if (estimateCount == 0)
      return new PokerStatistics(
          cards.size(),
          abstentions,
          List.copyOf(distribution),
          null,
          null,
          null,
          List.of(),
          false,
          0,
          null,
          0,
          null,
          null,
          true);

    List<String> modes = new ArrayList<>();
    for (int index = 0; index < counts.length; index++)
      if (PokerDeck.estimates(deck.get(index)) && counts[index] == highest)
        modes.add(deck.get(index));

    String min = deck.get(sorted[0]);
    String max = deck.get(sorted[estimateCount - 1]);
    String median = deck.get(sorted[(estimateCount - 1) / 2]);
    int spread = sorted[estimateCount - 1] - sorted[0];
    boolean consensus = spread == 0;
    return new PokerStatistics(
        cards.size(),
        abstentions,
        List.copyOf(distribution),
        min,
        max,
        median,
        List.copyOf(modes),
        consensus,
        (double) highest / estimateCount,
        mean(Arrays.stream(sorted).mapToObj(deck::get).toList()),
        spread,
        median,
        storyPoints(median),
        spread > 1);
  }

  /** The numeric value of a card, or {@code null} when it is not a plain number. */
  static Double numeric(String card) {
    if (card == null || !NUMBER.matcher(card).matches()) return null;
    return "½".equals(card) ? 0.5 : Double.parseDouble(card);
  }

  /** The card as story points when a story accepts that value. */
  static Integer storyPoints(String card) {
    Double value = numeric(card);
    if (value == null || value != Math.rint(value)) return null;
    int points = value.intValue();
    return STORY_POINTS.contains(points) ? points : null;
  }

  private static Double mean(List<String> estimates) {
    double sum = 0;
    for (String card : estimates) {
      Double value = numeric(card);
      if (value == null) return null;
      sum += value;
    }
    return sum / estimates.size();
  }
}
