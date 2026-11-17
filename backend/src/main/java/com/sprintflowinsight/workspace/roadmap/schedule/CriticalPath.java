package com.sprintflowinsight.workspace.roadmap.schedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The critical path method (CPM) on an acyclic dependency graph whose durations are PERT means.
 *
 * <p>A forward pass gives each task its earliest start and finish; the longest chain (by expected
 * duration) is the critical path. Ties are broken towards the chain with the larger accumulated
 * variance, which is the conservative choice for the PERT probability, and then towards the
 * smallest task id. A backward pass gives each task its slack: how many working days it can slip
 * without delaying the whole network. Tasks without an estimate take no time but still pass their
 * predecessors' finish on to their successors.
 *
 * @param duration expected length of the network in working days
 * @param variance sum of the variances along {@link #path}
 * @param path the critical chain, first task first; empty for an empty network
 * @param slack per task, in working days; 0 (within rounding) on every critical task
 */
public record CriticalPath(
    double duration, double variance, List<Long> path, Map<Long, Double> slack) {
  /** Durations are sums of decimals, so equality is decided within this tolerance. */
  public static final double EPSILON = 1e-9;

  public CriticalPath {
    path = List.copyOf(path);
    slack = Collections.unmodifiableMap(new TreeMap<>(slack));
  }

  public double standardDeviation() {
    return Math.sqrt(variance);
  }

  /** True when the task has no slack. */
  public boolean critical(long task) {
    Double value = slack.get(task);
    return value != null && value <= EPSILON;
  }

  /**
   * Analyses {@code graph}.
   *
   * @param estimates three-point estimates by task; missing tasks take zero time
   * @throws IllegalStateException when the graph has a cycle
   */
  public static CriticalPath analyse(DependencyGraph graph, Map<Long, ThreePointEstimate> estimates) {
    List<Long> order = graph.topologicalOrder();
    if (order.isEmpty()) return new CriticalPath(0, 0, List.of(), Map.of());
    Map<Long, Double> finish = new HashMap<>();
    Map<Long, Double> chainVariance = new HashMap<>();
    Map<Long, Long> via = new HashMap<>();
    for (long task : order) {
      double start = 0;
      double variance = 0;
      Long best = null;
      for (long before : graph.predecessors(task)) {
        double candidate = finish.get(before);
        double candidateVariance = chainVariance.get(before);
        if (best == null || better(candidate, candidateVariance, before, start, variance, best)) {
          start = candidate;
          variance = candidateVariance;
          best = before;
        }
      }
      ThreePointEstimate estimate = estimates.get(task);
      finish.put(task, start + (estimate == null ? 0 : estimate.mean()));
      chainVariance.put(task, variance + (estimate == null ? 0 : estimate.variance()));
      if (best != null) via.put(task, best);
    }
    long end = order.get(0);
    for (long task : order) {
      if (better(finish.get(task), chainVariance.get(task), task, finish.get(end), chainVariance.get(end), end))
        end = task;
    }
    double duration = finish.get(end);
    List<Long> path = new ArrayList<>();
    for (Long step = end; step != null; step = via.get(step)) path.add(step);
    Collections.reverse(path);

    Map<Long, Double> latestFinish = new HashMap<>();
    Map<Long, Double> slack = new HashMap<>();
    for (int i = order.size() - 1; i >= 0; i--) {
      long task = order.get(i);
      double latest = duration;
      for (long after : graph.successors(task)) {
        ThreePointEstimate next = estimates.get(after);
        latest = Math.min(latest, latestFinish.get(after) - (next == null ? 0 : next.mean()));
      }
      latestFinish.put(task, latest);
      slack.put(task, Math.max(0, latest - finish.get(task)));
    }
    return new CriticalPath(duration, chainVariance.get(end), path, slack);
  }

  private static boolean better(
      double finish, double variance, long id, double bestFinish, double bestVariance, long bestId) {
    if (finish > bestFinish + EPSILON) return true;
    if (finish < bestFinish - EPSILON) return false;
    if (variance > bestVariance + EPSILON) return true;
    if (variance < bestVariance - EPSILON) return false;
    return id < bestId;
  }
}
