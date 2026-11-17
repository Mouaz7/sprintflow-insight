package com.sprintflowinsight.workspace.roadmap.schedule;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DependencyGraphTest {
  @Test
  void reportsTheActualCycleWithoutChangingTheGraph() {
    var graph = DependencyGraph.of(List.of(), List.of(new Edge(1, 2), new Edge(2, 3)));
    assertThat(graph.cycleIfAdded(3, 1)).hasValue(List.of(3L, 1L, 2L, 3L));
    assertThat(graph.cycleIfAdded(1, 3)).isEmpty();
    assertThat(graph.cycleIfAdded(4, 4)).hasValue(List.of(4L, 4L));
    assertThat(graph.findCycle()).isEmpty();
    graph.add(3, 1);
    assertThat(graph.findCycle()).hasValue(List.of(1L, 2L, 3L, 1L));
    assertThatThrownBy(graph::topologicalOrder).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void ordersReadyNodesDeterministicallyAndKeepsIsolatedTasks() {
    var graph = DependencyGraph.of(List.of(4L), List.of(new Edge(2, 3), new Edge(1, 3)));
    assertThat(graph.topologicalOrder()).containsExactly(1L, 2L, 3L, 4L);
    assertThat(graph.successors(1)).containsExactly(3L);
    assertThat(graph.predecessors(3)).containsExactly(1L, 2L);
    assertThat(graph.successors(999)).isEmpty();
    assertThat(graph.predecessors(999)).isEmpty();
    assertThatThrownBy(() -> graph.nodes().add(10L))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> graph.successors(1).clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void forecastsIncludeOutsideAncestorsButNotUnrelatedWork() {
    var graph = DependencyGraph.of(List.of(4L), List.of(new Edge(1, 2), new Edge(2, 3)));
    assertThat(graph.withAncestors(List.of(3L))).containsExactly(1L, 2L, 3L);
    assertThat(graph.restrictedTo(graph.withAncestors(List.of(3L))).topologicalOrder())
        .containsExactly(1L, 2L, 3L);
    assertThat(graph.restrictedTo(Set.of(1L, 3L, 999L)).topologicalOrder()).containsExactly(1L, 3L);
    assertThat(graph.restrictedTo(Set.of()).nodes()).isEmpty();
  }

  @Test
  void aDeepDependencyChainCannotOverflowTheStack() {
    var edges = new ArrayList<Edge>();
    for (long id = 1; id < 20_000; id++) edges.add(new Edge(id, id + 1));
    var graph = DependencyGraph.of(List.of(), edges);
    assertThat(graph.topologicalOrder()).hasSize(20_000);
    assertThat(graph.findCycle()).isEmpty();
    var cycle = graph.cycleIfAdded(20_000, 1).orElseThrow();
    assertThat(cycle).hasSize(20_001).startsWith(20_000L, 1L).endsWith(20_000L);
    graph.add(20_000, 1);
    assertThat(graph.findCycle().orElseThrow()).hasSize(20_001);
  }
}
