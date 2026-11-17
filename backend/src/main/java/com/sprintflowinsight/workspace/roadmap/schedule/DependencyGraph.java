package com.sprintflowinsight.workspace.roadmap.schedule;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A directed graph of task precedences. Adjacency is kept in sorted maps so every traversal, and
 * therefore every reported cycle and order, is deterministic. All traversals are iterative, so deep
 * chains cannot overflow the stack.
 */
public final class DependencyGraph {
  private static final TreeSet<Long> EMPTY = new TreeSet<>();
  private final Map<Long, TreeSet<Long>> successors = new TreeMap<>();
  private final Map<Long, TreeSet<Long>> predecessors = new TreeMap<>();

  /** A graph containing the given nodes (possibly isolated) and edges. */
  public static DependencyGraph of(Collection<Long> nodes, Collection<Edge> edges) {
    DependencyGraph graph = new DependencyGraph();
    nodes.forEach(graph::addNode);
    edges.forEach(edge -> graph.add(edge.from(), edge.to()));
    return graph;
  }

  public void addNode(long node) {
    successors.computeIfAbsent(node, key -> new TreeSet<>());
    predecessors.computeIfAbsent(node, key -> new TreeSet<>());
  }

  /** Adds an edge without checking for cycles; use {@link #cycleIfAdded} first. */
  public void add(long from, long to) {
    addNode(from);
    addNode(to);
    successors.get(from).add(to);
    predecessors.get(to).add(from);
  }

  public Set<Long> nodes() {
    return Collections.unmodifiableSet(successors.keySet());
  }

  public Set<Long> successors(long node) {
    return Collections.unmodifiableSet(successors.getOrDefault(node, EMPTY));
  }

  public Set<Long> predecessors(long node) {
    return Collections.unmodifiableSet(predecessors.getOrDefault(node, EMPTY));
  }

  /**
   * The cycle that adding {@code from -> to} would close, found by a depth-first search from {@code
   * to} for a path back to {@code from}. The result starts and ends with {@code from}: {@code
   * [from, to, ..., from]}. Empty when the edge keeps the graph acyclic.
   */
  public Optional<List<Long>> cycleIfAdded(long from, long to) {
    if (from == to) return Optional.of(List.of(from, from));
    Map<Long, Long> parent = new HashMap<>();
    Deque<Long> stack = new ArrayDeque<>();
    stack.push(to);
    parent.put(to, to);
    while (!stack.isEmpty()) {
      long node = stack.pop();
      if (node == from) {
        List<Long> path = new ArrayList<>();
        for (long step = from; ; step = parent.get(step)) {
          path.add(step);
          if (step == to) break;
        }
        path.add(from);
        Collections.reverse(path);
        return Optional.of(List.copyOf(path));
      }
      // Push in reverse so the smallest successor is explored first.
      for (Iterator<Long> it = successors.getOrDefault(node, EMPTY).descendingIterator();
          it.hasNext(); ) {
        long next = it.next();
        if (!parent.containsKey(next)) {
          parent.put(next, node);
          stack.push(next);
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Any cycle in the graph, found with a three-colour depth-first search: reaching a node that is
   * still on the current path closes a cycle. The result starts and ends with the same node.
   */
  public Optional<List<Long>> findCycle() {
    Map<Long, Integer> colour = new HashMap<>(); // absent = white, 1 = on path, 2 = finished
    for (long root : successors.keySet()) {
      if (colour.containsKey(root)) continue;
      Deque<Long> path = new ArrayDeque<>();
      Deque<Iterator<Long>> pending = new ArrayDeque<>();
      path.addLast(root);
      pending.addLast(successors.get(root).iterator());
      colour.put(root, 1);
      while (!path.isEmpty()) {
        Iterator<Long> next = pending.peekLast();
        if (next.hasNext()) {
          long child = next.next();
          Integer state = colour.get(child);
          if (state == null) {
            colour.put(child, 1);
            path.addLast(child);
            pending.addLast(successors.get(child).iterator());
          } else if (state == 1) {
            List<Long> cycle = new ArrayList<>();
            boolean inside = false;
            for (long node : path) {
              if (node == child) inside = true;
              if (inside) cycle.add(node);
            }
            cycle.add(child);
            return Optional.of(List.copyOf(cycle));
          }
        } else {
          colour.put(path.removeLast(), 2);
          pending.removeLast();
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Every node with each predecessor before its successors; among ready nodes the smallest id comes
   * first (Kahn's algorithm).
   *
   * @throws IllegalStateException when the graph has a cycle
   */
  public List<Long> topologicalOrder() {
    Map<Long, Integer> missing = new HashMap<>();
    PriorityQueue<Long> ready = new PriorityQueue<>();
    predecessors.forEach(
        (node, before) -> {
          missing.put(node, before.size());
          if (before.isEmpty()) ready.add(node);
        });
    List<Long> order = new ArrayList<>(successors.size());
    while (!ready.isEmpty()) {
      long node = ready.poll();
      order.add(node);
      for (long next : successors.get(node)) {
        if (missing.merge(next, -1, Integer::sum) == 0) ready.add(next);
      }
    }
    if (order.size() != successors.size())
      throw new IllegalStateException("The dependency graph contains a cycle");
    return List.copyOf(order);
  }

  /** The given nodes together with everything that (transitively) precedes them. */
  public Set<Long> withAncestors(Collection<Long> nodes) {
    Set<Long> seen = new TreeSet<>();
    Deque<Long> queue = new ArrayDeque<>();
    for (long node : nodes) if (seen.add(node)) queue.add(node);
    while (!queue.isEmpty()) {
      for (long before : predecessors.getOrDefault(queue.poll(), EMPTY))
        if (seen.add(before)) queue.add(before);
    }
    return Collections.unmodifiableSet(seen);
  }

  /** The subgraph induced by {@code keep}. */
  public DependencyGraph restrictedTo(Set<Long> keep) {
    DependencyGraph sub = new DependencyGraph();
    for (long node : keep) {
      if (!successors.containsKey(node)) continue;
      sub.addNode(node);
      for (long next : successors.get(node)) if (keep.contains(next)) sub.add(node, next);
    }
    return sub;
  }
}
