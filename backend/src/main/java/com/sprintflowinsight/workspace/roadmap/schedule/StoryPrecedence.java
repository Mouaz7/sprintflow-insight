package com.sprintflowinsight.workspace.roadmap.schedule;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Lifts task dependencies to story precedences for sprint planning: story B depends on story A
 * when an open task of A blocks a task of B. Task graphs are acyclic, but the lifted story graph
 * need not be (A's first task blocks B while B's second task blocks A). Stories in such a cycle
 * can only be planned together, so precedences inside one strongly connected component are
 * dropped and only precedences between components are kept; the result is always acyclic.
 */
public final class StoryPrecedence {
  private StoryPrecedence() {}

  /**
   * @param storyOfTask the story each task belongs to; tasks without a story are ignored
   * @param openEdges dependencies whose blocking task is not finished
   * @return predecessor stories by story, both sorted
   */
  public static Map<Long, Set<Long>> predecessors(
      Map<Long, Long> storyOfTask, Collection<Edge> openEdges) {
    DependencyGraph stories = new DependencyGraph();
    for (Edge edge : openEdges) {
      Long from = storyOfTask.get(edge.from());
      Long to = storyOfTask.get(edge.to());
      if (from != null && to != null && !from.equals(to)) stories.add(from, to);
    }
    Map<Long, Integer> component = new HashMap<>();
    List<Set<Long>> components = stories.stronglyConnectedComponents();
    for (int i = 0; i < components.size(); i++)
      for (long story : components.get(i)) component.put(story, i);
    Map<Long, Set<Long>> result = new TreeMap<>();
    for (long story : stories.nodes()) {
      for (long before : stories.predecessors(story)) {
        if (!component.get(before).equals(component.get(story)))
          result.computeIfAbsent(story, key -> new TreeSet<>()).add(before);
      }
    }
    return result;
  }
}
