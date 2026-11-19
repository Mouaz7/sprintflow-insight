package com.sprintflowinsight.workspace.poker;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;

/**
 * One planning poker session for one story (Flyway V23). Never serialised directly: clients get a
 * {@link PokerSessionView}, which decides what a caller may see. Votes live in {@code poker_vote}
 * ({@link PokerVotes}).
 */
@Entity
@Table(name = "poker_session")
public class PokerSession {
  public enum Status {
    /** The current round accepts votes; nobody sees the values. */
    VOTING,
    /** The current round is revealed; the facilitator may revote or accept. */
    REVEALED,
    /** Closed with an estimate written to the story. */
    ACCEPTED,
    /** Closed without an estimate; the unrevealed round's votes were deleted. */
    CANCELLED;

    public boolean open() {
      return this == VOTING || this == REVEALED;
    }
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long projectId;

  @Column(nullable = false)
  private Long storyId;

  private Long facilitatorUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private PokerDeck deck;

  @Convert(converter = CardsConverter.class)
  @Column(nullable = false, length = 1000)
  private List<String> cards;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status;

  @Column(nullable = false)
  private int round;

  @Column(nullable = false)
  private int revealedRound;

  @Column(length = 8)
  private String finalValue;

  private Integer finalStoryPoints;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  private Instant closedAt;

  @Version private Long version;

  protected PokerSession() {}

  PokerSession(
      Long projectId,
      Long storyId,
      Long facilitatorUserId,
      PokerDeck deck,
      List<String> cards,
      Instant now) {
    this.projectId = projectId;
    this.storyId = storyId;
    this.facilitatorUserId = facilitatorUserId;
    this.deck = deck;
    this.cards = List.copyOf(cards);
    this.status = Status.VOTING;
    this.round = 1;
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** Marks the session changed, so its version (and the realtime version) moves on. */
  void touch(Instant now) {
    updatedAt = now;
  }

  void reveal(Instant now) {
    status = Status.REVEALED;
    revealedRound = round;
    updatedAt = now;
  }

  void revote(Instant now) {
    status = Status.VOTING;
    round++;
    updatedAt = now;
  }

  void accept(String value, int storyPoints, Instant now) {
    status = Status.ACCEPTED;
    finalValue = value;
    finalStoryPoints = storyPoints;
    closedAt = now;
    updatedAt = now;
  }

  void cancel(Instant now) {
    status = Status.CANCELLED;
    closedAt = now;
    updatedAt = now;
  }

  /** The last round whose votes may be shown; 0 before the first reveal. */
  public int getRevealedRound() {
    return revealedRound;
  }

  public Long getId() {
    return id;
  }

  public Long getProjectId() {
    return projectId;
  }

  public Long getStoryId() {
    return storyId;
  }

  public Long getFacilitatorUserId() {
    return facilitatorUserId;
  }

  public PokerDeck getDeck() {
    return deck;
  }

  public List<String> getCards() {
    return cards;
  }

  public Status getStatus() {
    return status;
  }

  public int getRound() {
    return round;
  }

  public String getFinalValue() {
    return finalValue;
  }

  public Integer getFinalStoryPoints() {
    return finalStoryPoints;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public Long getVersion() {
    return version;
  }
}
