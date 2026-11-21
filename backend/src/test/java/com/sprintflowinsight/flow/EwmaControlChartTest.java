package com.sprintflowinsight.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import java.util.Collections;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class EwmaControlChartTest {
  @Property(tries = 150, seed = "2026112101")
  void steadyFlowStaysWithinItsLimits(@ForAll @IntRange(min = 0, max = 10000) int count) {
    var chart =
        EwmaControlChart.compare(Collections.nCopies(28, count), Collections.nCopies(7, count));
    assertThat(chart.monitoring())
        .allSatisfy(
            point -> {
              assertThat(point.weighted()).isEqualTo(count);
              assertThat(point.lower()).isBetween(0.0, point.weighted());
              assertThat(point.upper()).isGreaterThan(point.weighted());
              assertThat(point.signal()).isEqualTo(EwmaControlChart.Signal.IN_LIMITS);
            });
  }

  @Test
  void weightsSequentialPeriodsAndBuildsLimitsOnlyFromThePriorWindow() {
    var chart =
        EwmaControlChart.compare(Collections.nCopies(28, 16), List.of(18, 18, 18, 18, 18, 18, 18));
    assertThat(chart.baselineMean()).isEqualTo(16);
    var first = chart.monitoring().getFirst();
    assertThat(first.observed()).isEqualTo(18);
    assertThat(first.weighted()).isCloseTo(16.5, within(1e-9));
    assertThat(first.lower()).isCloseTo(13, within(1e-9));
    assertThat(first.upper()).isCloseTo(19, within(1e-9));
    assertThat(first.signal()).isEqualTo(EwmaControlChart.Signal.IN_LIMITS);
    assertThat(chart.monitoring().get(1).weighted()).isCloseTo(16.875, within(1e-9));
    assertThat(chart.monitoring().get(1).upper()).isCloseTo(19.75, within(1e-9));
  }

  @Test
  void showsStepChangesInEitherDirectionAndClipsNegativeCountLimits() {
    var high = EwmaControlChart.compare(Collections.nCopies(28, 12), Collections.nCopies(7, 48));
    var low = EwmaControlChart.compare(Collections.nCopies(28, 12), Collections.nCopies(7, 0));
    assertThat(high.monitoring().getLast().signal()).isEqualTo(EwmaControlChart.Signal.HIGH);
    assertThat(low.monitoring().getLast().signal()).isEqualTo(EwmaControlChart.Signal.LOW);
    var idle = EwmaControlChart.compare(Collections.nCopies(28, 0), Collections.nCopies(7, 0));
    assertThat(idle.monitoring())
        .allSatisfy(
            point -> {
              assertThat(point.lower()).isZero();
              assertThat(point.upper()).isPositive();
              assertThat(point.signal()).isEqualTo(EwmaControlChart.Signal.IN_LIMITS);
            });
  }

  @Test
  void requiresFixedCompleteWindowsOfNonNegativeCounts() {
    var baseline = Collections.nCopies(28, 3);
    var monitoring = Collections.nCopies(7, 3);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> EwmaControlChart.compare(baseline, List.of(3)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> EwmaControlChart.compare(List.of(3), monitoring));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> EwmaControlChart.compare(Collections.nCopies(28, -1), monitoring));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> EwmaControlChart.compare(baseline, Collections.nCopies(7, -1)));
  }
}
