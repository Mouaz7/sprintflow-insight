package com.sprintflowinsight.planning.optimizer;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A story that may be put into the sprint.
 *
 * @param id stable identifier, unique in the problem
 * @param value business value gained when the story is chosen (non-negative)
 * @param points estimate that consumes the team's total capacity (non-negative)
 * @param effortByMember effort the story needs from each named member; empty when the team plans
 *     with total capacity only
 * @param predecessorIds stories that must be done already or chosen in the same plan
 * @param lock manual lock-in or lock-out
 */
public record CandidateStory(
    String id,
    long value,
    int points,
    Map<String, Integer> effortByMember,
    List<String> predecessorIds,
    Lock lock) {

  /** Upper bound for a single value, so that sums over large backlogs cannot overflow. */
  public static final long MAX_VALUE = 1_000_000_000L;

  /** Upper bound for points and effort, so that the dynamic program's table stays bounded. */
  public static final int MAX_POINTS = 1_000_000;

  public CandidateStory {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("Story id must not be blank");
    }
    if (value < 0 || value > MAX_VALUE) {
      throw new IllegalArgumentException("Story " + id + " has value outside 0.." + MAX_VALUE);
    }
    checkAmount(id, "points", points);
    Objects.requireNonNull(lock, "lock");
    var efforts = new TreeMap<String, Integer>();
    (effortByMember == null ? Map.<String, Integer>of() : effortByMember)
        .forEach(
            (member, effort) -> {
              if (member == null || member.isBlank() || effort == null) {
                throw new IllegalArgumentException("Story " + id + " has an invalid member effort");
              }
              checkAmount(id, "effort", effort);
              efforts.put(member, effort);
            });
    var predecessors = new TreeSet<String>();
    for (String predecessor : predecessorIds == null ? List.<String>of() : predecessorIds) {
      if (predecessor == null || predecessor.isBlank() || predecessor.equals(id)) {
        throw new IllegalArgumentException("Story " + id + " has an invalid predecessor");
      }
      predecessors.add(predecessor);
    }
    effortByMember = Collections.unmodifiableMap(efforts);
    predecessorIds = List.copyOf(predecessors);
  }

  /** A story without member effort, dependencies or lock. */
  public static CandidateStory of(String id, long value, int points) {
    return new CandidateStory(id, value, points, Map.of(), List.of(), Lock.NONE);
  }

  /** Copy with the given predecessors. */
  public CandidateStory withPredecessors(String... ids) {
    return new CandidateStory(id, value, points, effortByMember, List.of(ids), lock);
  }

  /** Copy with the given lock. */
  public CandidateStory withLock(Lock newLock) {
    return new CandidateStory(id, value, points, effortByMember, predecessorIds, newLock);
  }

  /** Copy with the given per-member effort. */
  public CandidateStory withEffort(Map<String, Integer> efforts) {
    return new CandidateStory(id, value, points, efforts, predecessorIds, lock);
  }

  private static void checkAmount(String id, String name, int amount) {
    if (amount < 0 || amount > MAX_POINTS) {
      throw new IllegalArgumentException(
          "Story " + id + " has " + name + " outside 0.." + MAX_POINTS);
    }
  }
}
