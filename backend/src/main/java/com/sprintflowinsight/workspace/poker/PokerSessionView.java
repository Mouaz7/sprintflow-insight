package com.sprintflowinsight.workspace.poker;

import java.time.Instant;
import java.util.List;

/**
 * A session as one caller may see it. Votes of the current round appear only after the reveal;
 * until then {@link #voters} lists who voted, without cards, and {@link #myCard} is the caller's
 * own card. Earlier rounds were revealed before their revote and are always shown in {@link
 * #rounds}.
 *
 * @param rounds revealed rounds, oldest first, each with its votes and statistics
 * @param canVote the caller may vote now (writer, session voting, project active)
 * @param canFacilitate the caller may reveal, revote, accept and cancel
 */
public record PokerSessionView(
    Long id,
    Long projectId,
    Long storyId,
    String storyTitle,
    Integer storyPoints,
    Long storyVersion,
    PokerDeck deck,
    List<String> cards,
    PokerSession.Status status,
    int round,
    Long facilitatorUserId,
    List<Voter> voters,
    String myCard,
    List<Round> rounds,
    String finalValue,
    Integer finalStoryPoints,
    boolean canVote,
    boolean canFacilitate,
    Instant createdAt,
    Instant closedAt,
    Long version) {

  /** Someone who voted in the current round. */
  public record Voter(Long userId, String name) {}

  /** A revealed vote. */
  public record Vote(Long userId, String name, String card) {}

  /** A revealed round. */
  public record Round(int number, List<Vote> votes, PokerStatistics statistics) {}
}
