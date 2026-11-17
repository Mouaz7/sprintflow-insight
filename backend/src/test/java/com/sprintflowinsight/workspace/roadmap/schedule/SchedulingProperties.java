package com.sprintflowinsight.workspace.roadmap.schedule;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class SchedulingProperties {
  @Property(tries = 150, seed = "801031")
  void cycleDetectionMatchesIndependentTransitiveClosure(
      @ForAll @IntRange(min = 0, max = 32767) int mask) {
    var edges = edges(6, mask);
    var graph = DependencyGraph.of(List.of(0L, 1L, 2L, 3L, 4L, 5L), edges);
    boolean[][] reach = new boolean[6][6];
    for (var edge : edges) reach[(int) edge.from()][(int) edge.to()] = true;
    for (int via = 0; via < 6; via++)
      for (int a = 0; a < 6; a++)
        for (int b = 0; b < 6; b++) reach[a][b] |= reach[a][via] && reach[via][b];
    for (int from = 0; from < 6; from++)
      for (int to = 0; to < 6; to++)
        assertThat(graph.cycleIfAdded(from, to).isPresent())
            .isEqualTo(from == to || reach[to][from]);
    assertThat(graph.findCycle()).isEmpty();
    var order = graph.topologicalOrder();
    for (var edge : edges)
      assertThat(order.indexOf(edge.from())).isLessThan(order.indexOf(edge.to()));
  }

  @Property(tries = 150, seed = "801032")
  void criticalDurationMatchesExhaustivePaths(
      @ForAll @IntRange(min = 0, max = 63) int mask,
      @ForAll @IntRange(min = 0, max = 10000) int durationSeed) {
    var edges = edges(4, mask);
    var graph = DependencyGraph.of(List.of(0L, 1L, 2L, 3L), edges);
    int[] durations = new int[4];
    Map<Long, ThreePointEstimate> estimates = new HashMap<>();
    for (int i = 0; i < 4; i++) {
      durations[i] = (durationSeed / (int) Math.pow(10, i)) % 10;
      estimates.put((long) i, new ThreePointEstimate(durations[i], durations[i], durations[i]));
    }
    int longest = 0;
    for (int start = 0; start < 4; start++)
      longest = Math.max(longest, longest(start, edges, durations));
    var result = CriticalPath.analyse(graph, estimates);
    assertThat(result.duration()).isEqualTo(longest);
    assertThat(result.path().stream().mapToInt(id -> durations[id.intValue()]).sum())
        .isEqualTo(longest);
    for (int i = 1; i < result.path().size(); i++)
      assertThat(edges).contains(new Edge(result.path().get(i - 1), result.path().get(i)));
  }

  private static int longest(int node, List<Edge> edges, int[] durations) {
    int remaining = 0;
    for (var edge : edges)
      if (edge.from() == node)
        remaining = Math.max(remaining, longest((int) edge.to(), edges, durations));
    return durations[node] + remaining;
  }

  private static List<Edge> edges(int nodes, int mask) {
    var edges = new ArrayList<Edge>();
    int bit = 0;
    for (int from = 0; from < nodes; from++)
      for (int to = from + 1; to < nodes; to++)
        if ((mask & (1 << bit++)) != 0) edges.add(new Edge(from, to));
    return edges;
  }
}
