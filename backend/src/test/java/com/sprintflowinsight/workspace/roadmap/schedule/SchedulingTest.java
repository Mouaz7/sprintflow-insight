package com.sprintflowinsight.workspace.roadmap.schedule;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SchedulingTest {
  private static ThreePointEstimate days(double value) {
    return new ThreePointEstimate(value, value, value);
  }

  @Test
  void parallelWorkUsesTheLongestChainAndReportsSlack() {
    var graph =
        DependencyGraph.of(
            List.of(), List.of(new Edge(1, 2), new Edge(1, 3), new Edge(2, 4), new Edge(3, 4)));
    var path =
        CriticalPath.analyse(graph, Map.of(1L, days(2), 2L, days(5), 3L, days(1), 4L, days(3)));
    assertThat(path.duration()).isEqualTo(10);
    assertThat(path.path()).containsExactly(1L, 2L, 4L);
    assertThat(path.slack()).containsEntry(3L, 4d).containsEntry(2L, 0d);
    assertThat(path.critical(1)).isTrue();
    assertThat(path.critical(3)).isFalse();
    assertThat(path.critical(999)).isFalse();
  }

  @Test
  void equalLengthChainsPreferHigherVarianceThenStableIds() {
    var graph = DependencyGraph.of(List.of(), List.of(new Edge(1, 3), new Edge(2, 3)));
    var risk =
        CriticalPath.analyse(
            graph, Map.of(1L, days(2), 2L, new ThreePointEstimate(0, 1, 8), 3L, days(1)));
    assertThat(risk.path()).containsExactly(2L, 3L);
    assertThat(risk.variance()).isCloseTo(16d / 9, within(1e-12));
    var tie = CriticalPath.analyse(graph, Map.of(1L, days(2), 2L, days(2), 3L, days(1)));
    assertThat(tie.path()).containsExactly(1L, 3L);
  }

  @Test
  void missingDurationsDoNotLoseTheirPredecessorsAndEmptyNetworksAreZero() {
    var graph = DependencyGraph.of(List.of(), List.of(new Edge(1, 2), new Edge(2, 3)));
    var path = CriticalPath.analyse(graph, Map.of(1L, days(2), 3L, days(3)));
    assertThat(path.duration()).isEqualTo(5);
    assertThat(path.path()).containsExactly(1L, 2L, 3L);
    assertThat(CriticalPath.analyse(DependencyGraph.of(List.of(), List.of()), Map.of()).path())
        .isEmpty();
    graph.add(3, 1);
    assertThatThrownBy(() -> CriticalPath.analyse(graph, Map.of()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void estimatesHaveKnownMeanVarianceAndStrictBounds() {
    var estimate = new ThreePointEstimate(1, 2, 9);
    assertThat(estimate.mean()).isEqualTo(3);
    assertThat(estimate.variance()).isCloseTo(16d / 9, within(1e-12));
    assertThat(estimate.standardDeviation()).isCloseTo(4d / 3, within(1e-12));
    assertThat(ThreePointEstimate.valid(0, 0, 1000)).isTrue();
    for (double[] bad :
        new double[][] {
          {-1, 1, 2},
          {2, 1, 3},
          {0, 3, 2},
          {0, 1, 1001},
          {Double.NaN, 1, 2},
          {0, Double.POSITIVE_INFINITY, 2},
          {0, 1, Double.NEGATIVE_INFINITY}
        }) {
      assertThat(ThreePointEstimate.valid(bad[0], bad[1], bad[2])).isFalse();
      assertThatThrownBy(() -> new ThreePointEstimate(bad[0], bad[1], bad[2]))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void calendarCountsStartDayAndSkipsWeekends() {
    var friday = LocalDate.of(2026, 11, 13);
    assertThat(WorkingDays.finish(friday, 3)).isEqualTo(LocalDate.of(2026, 11, 17));
    assertThat(WorkingDays.between(friday, LocalDate.of(2026, 11, 17))).isEqualTo(3);
    assertThat(WorkingDays.finish(friday.plusDays(2), 1)).isEqualTo(friday.plusDays(3));
    assertThat(WorkingDays.finish(friday, 0)).isEqualTo(friday);
    assertThat(WorkingDays.between(friday, friday.minusDays(1))).isZero();
    assertThat(WorkingDays.between(friday.plusDays(1), friday.plusDays(2))).isZero();
    assertThat(WorkingDays.finish(friday, 6)).isEqualTo(friday.plusWeeks(1));
  }

  @ParameterizedTest
  @CsvSource({
    "-3,0.0013498980316301",
    "-1,0.1586552539314571",
    "0,0.5",
    "1,0.8413447460685429",
    "1.96,0.9750021048517795",
    "3,0.9986501019683699",
    "8,0.9999999999999993"
  })
  void normalProbabilitiesMatchIndependentReferenceValues(double z, double expected) {
    assertThat(StandardNormal.cdf(z)).isCloseTo(expected, within(7.5e-8));
  }

  @Test
  void normalAndPertBoundariesAreWellDefined() {
    assertThat(StandardNormal.cdf(Double.NEGATIVE_INFINITY)).isZero();
    assertThat(StandardNormal.cdf(Double.POSITIVE_INFINITY)).isEqualTo(1);
    assertThatThrownBy(() -> StandardNormal.cdf(Double.NaN))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(PertForecast.probabilityWithin(10, 0, 9)).isZero();
    assertThat(PertForecast.probabilityWithin(10, 0, 10)).isEqualTo(1);
    assertThat(PertForecast.probabilityWithin(10, 2, 10)).isEqualTo(0.5);
    var path =
        CriticalPath.analyse(DependencyGraph.of(List.of(1L), List.of()), Map.of(1L, days(2)));
    var monday = LocalDate.of(2026, 11, 16);
    assertThat(PertForecast.of(path, monday, monday.plusDays(1)).probability()).isEqualTo(1);
    assertThat(PertForecast.of(path, monday, monday.minusDays(1)).probability()).isZero();
    assertThat(PertForecast.of(path, monday, null).probability()).isNull();
  }
}
