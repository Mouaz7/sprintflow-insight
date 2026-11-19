package com.sprintflowinsight.workspace.poker;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Votes ({@code poker_vote}). Only {@link PokerService} reads values, and it decides per round
 * whether they may leave the server. Nothing here logs.
 */
@Repository
class PokerVotes {
  /** One stored vote with the voter's public name (never an email). */
  record Vote(int round, long userId, String name, String card) {}

  private final JdbcTemplate jdbc;

  PokerVotes(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Casts or changes the caller's vote for a round. Callers hold the session row lock. */
  @Transactional(propagation = Propagation.MANDATORY)
  void cast(long sessionId, int round, long userId, String card, Instant now) {
    jdbc.update(
        "insert into poker_vote(session_id,round,user_id,card,voted_at) values (?,?,?,?,?)"
            + " on conflict (session_id,round,user_id)"
            + " do update set card=excluded.card, voted_at=excluded.voted_at",
        sessionId,
        round,
        userId,
        card,
        Timestamp.from(now));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  boolean retract(long sessionId, int round, long userId) {
    return jdbc.update(
            "delete from poker_vote where session_id=? and round=? and user_id=?",
            sessionId,
            round,
            userId)
        > 0;
  }

  /** Removes a round's votes, used when a session closes before that round was revealed. */
  @Transactional(propagation = Propagation.MANDATORY)
  void discard(long sessionId, int round) {
    jdbc.update("delete from poker_vote where session_id=? and round=?", sessionId, round);
  }

  int count(long sessionId, int round) {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from poker_vote where session_id=? and round=?",
            Integer.class,
            sessionId,
            round);
    return count == null ? 0 : count;
  }

  /** All votes of a session up to and including {@code lastRound}, by round then name. */
  List<Vote> upTo(long sessionId, int lastRound) {
    return jdbc.query(
        "select v.round, v.user_id, coalesce(u.display_name,'') as name, v.card"
            + " from poker_vote v join users u on u.id=v.user_id"
            + " where v.session_id=? and v.round<=?"
            + " order by v.round, lower(coalesce(u.display_name,'')), v.user_id",
        (row, index) ->
            new Vote(
                row.getInt("round"),
                row.getLong("user_id"),
                row.getString("name"),
                row.getString("card")),
        sessionId,
        lastRound);
  }

  /** Who voted in a round, without values: what everyone may see while voting. */
  List<Vote> votersOf(long sessionId, int round) {
    return jdbc.query(
        "select v.user_id, coalesce(u.display_name,'') as name"
            + " from poker_vote v join users u on u.id=v.user_id"
            + " where v.session_id=? and v.round=?"
            + " order by lower(coalesce(u.display_name,'')), v.user_id",
        (row, index) -> new Vote(round, row.getLong("user_id"), row.getString("name"), null),
        sessionId,
        round);
  }

  /** The caller's own card in a round. */
  Optional<String> own(long sessionId, int round, long userId) {
    return jdbc
        .queryForList(
            "select card from poker_vote where session_id=? and round=? and user_id=?",
            String.class,
            sessionId,
            round,
            userId)
        .stream()
        .findFirst();
  }
}
