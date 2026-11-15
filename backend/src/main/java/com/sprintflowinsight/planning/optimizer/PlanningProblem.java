package com.sprintflowinsight.planning.optimizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * One sprint planning question.
 *
 * <p>Dependencies between candidates must be acyclic: a cycle would force all its stories in or
 * out together, which in a backlog is almost always a data error, so it is rejected here.
 *
 * @param stories candidates in backlog order; the order breaks ties deterministically
 * @param doneStoryIds stories that are already done and satisfy dependencies
 * @param totalCapacity points the team can deliver in the sprint
 * @param memberCapacities effort each member can deliver; empty when planning with total capacity
 *     only. Every member named in a story's effort must be listed here.
 */
public record PlanningProblem(
    List<CandidateStory> stories,
    Set<String> doneStoryIds,
    int totalCapacity,
    Map<String, Integer> memberCapacities) {

  public PlanningProblem {
    stories = List.copyOf(stories);
    doneStoryIds = Collections.unmodifiableSet(new TreeSet<>(doneStoryIds));
    memberCapacities = Collections.unmodifiableMap(new TreeMap<>(memberCapacities));
    if (totalCapacity < 0) {
      throw new IllegalArgumentException("Total capacity must not be negative");
    }
    memberCapacities.forEach(
        (member, capacity) -> {
          if (member.isBlank() || capacity < 0) {
            throw new IllegalArgumentException("Invalid capacity for member " + member);
          }
        });
    var ids = new HashMap<String, Integer>();
    for (CandidateStory story : stories) {
      if (ids.put(story.id(), ids.size()) != null) {
        throw new IllegalArgumentException("Duplicate story " + story.id());
      }
      if (doneStoryIds.contains(story.id())) {
        throw new IllegalArgumentException("Story " + story.id() + " is both done and a candidate");
      }
      for (String member : story.effortByMember().keySet()) {
        if (!memberCapacities.containsKey(member)) {
          throw new IllegalArgumentException(
              "Story " + story.id() + " needs effort from unknown member " + member);
        }
      }
    }
    if (topologicalOrder(stories, ids).size() != stories.size()) {
      throw new IllegalArgumentException("Story dependencies contain a cycle");
    }
  }

  /** A problem with total capacity only and no done stories. */
  public static PlanningProblem of(int totalCapacity, List<CandidateStory> stories) {
    return new PlanningProblem(stories, Set.of(), totalCapacity, Map.of());
  }

  /** True when at least one member has an individual capacity. */
  public boolean hasMemberCapacities() {
    return !memberCapacities.isEmpty();
  }

  /**
   * Candidate indexes so that every candidate predecessor comes before its successors; ties keep
   * backlog order. Shorter than the input when the dependencies contain a cycle.
   */
  static List<Integer> topologicalOrder(List<CandidateStory> stories, Map<String, Integer> ids) {
    int n = stories.size();
    int[] missing = new int[n];
    List<List<Integer>> successors = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      successors.add(new ArrayList<>());
    }
    for (int i = 0; i < n; i++) {
      for (String predecessor : stories.get(i).predecessorIds()) {
        Integer p = ids.get(predecessor);
        if (p != null) {
          successors.get(p).add(i);
          missing[i]++;
        }
      }
    }
    var ready = new PriorityQueue<Integer>();
    for (int i = 0; i < n; i++) {
      if (missing[i] == 0) {
        ready.add(i);
      }
    }
    var order = new ArrayList<Integer>(n);
    while (!ready.isEmpty()) {
      int next = ready.poll();
      order.add(next);
      for (int successor : successors.get(next)) {
        if (--missing[successor] == 0) {
          ready.add(successor);
        }
      }
    }
    return order;
  }
}
