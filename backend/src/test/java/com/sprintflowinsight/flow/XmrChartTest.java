package com.sprintflowinsight.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.sprintflowinsight.flow.XmrChart.Signal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class XmrChartTest {
  @Test
  void computesWheelerLimitsFromTheMeanMovingRange() {
    // moving ranges 2, 2, 2, 2, 2 -> MR̄ = 2; mean = 11
    XmrChart chart = XmrChart.of(List.of(10d, 12d, 10d, 12d, 10d, 12d)).orElseThrow();
    assertThat(chart.mean()).isCloseTo(11, within(1e-12));
    assertThat(chart.movingRangeMean()).isCloseTo(2, within(1e-12));
    assertThat(chart.upperLimit()).isCloseTo(11 + 5.32, within(1e-12));
    assertThat(chart.lowerLimit()).isCloseTo(11 - 5.32, within(1e-12));
    assertThat(chart.upperRangeLimit()).isCloseTo(6.536, within(1e-12));
    assertThat(chart.points().getFirst().movingRange()).isNull();
    assertThat(chart.points().get(1).movingRange()).isEqualTo(2);
    assertThat(chart.signalCount()).isZero();
  }

  @Test
  void needsAMinimumNumberOfPoints() {
    assertThat(XmrChart.of(List.of(1d, 2d, 3d, 4d, 5d))).isEmpty();
    assertThat(XmrChart.of(List.of(1d, 2d, 3d, 4d, 5d, 6d))).isPresent();
  }

  @Test
  void flagsAPointOutsideTheLimitsAndItsLargeMovingRange() {
    List<Double> values = new ArrayList<>(List.of(10d, 11d, 10d, 11d, 10d, 11d, 10d, 11d, 10d));
    values.add(40d);
    XmrChart chart = XmrChart.of(values).orElseThrow();
    XmrChart.Point spike = chart.points().getLast();
    assertThat(spike.signals()).contains(Signal.OUTSIDE_LIMITS, Signal.LARGE_MOVING_RANGE);
    assertThat(chart.points().subList(0, 9))
        .allSatisfy(point -> assertThat(point.signals()).doesNotContain(Signal.OUTSIDE_LIMITS));
  }

  @Test
  void flagsALowPointOutsideTheLowerLimit() {
    List<Double> values = new ArrayList<>(List.of(50d, 51d, 50d, 51d, 50d, 51d, 50d, 51d, 50d));
    values.add(20d);
    XmrChart chart = XmrChart.of(values).orElseThrow();
    assertThat(chart.points().getLast().signals()).contains(Signal.OUTSIDE_LIMITS);
  }

  @Test
  void flagsEightSuccessiveValuesOnOneSide() {
    // Eight low values then eight high ones: both runs are signals.
    List<Double> values = new ArrayList<>();
    for (int i = 0; i < 8; i++) values.add(10d + (i % 2));
    for (int i = 0; i < 8; i++) values.add(14d + (i % 2));
    XmrChart chart = XmrChart.of(values).orElseThrow();
    assertThat(chart.points())
        .allSatisfy(point -> assertThat(point.signals()).contains(Signal.RUN_OF_EIGHT));
  }

  @Test
  void sevenValuesOnOneSideAreNotARun() {
    List<Double> values =
        Arrays.asList(10d, 11d, 10d, 11d, 10d, 11d, 10d, 20d, 21d, 20d, 21d, 20d, 21d, 20d);
    XmrChart chart = XmrChart.of(values).orElseThrow();
    assertThat(chart.points())
        .allSatisfy(point -> assertThat(point.signals()).doesNotContain(Signal.RUN_OF_EIGHT));
  }

  @Test
  void flagsThreeOfFourBeyondTheHalfWayLine() {
    // mean 10, MR̄ small; three of four values well above the half-way line but inside the limit.
    List<Double> values =
        List.of(10d, 9d, 10d, 9d, 10d, 9d, 10d, 9d, 10d, 9d, 12d, 12.2d, 10d, 12.1d, 9d, 10d);
    XmrChart chart = XmrChart.of(values).orElseThrow();
    double half = chart.mean() + (chart.upperLimit() - chart.mean()) / 2;
    assertThat(12d).isGreaterThan(half).isLessThan(chart.upperLimit());
    assertThat(chart.points().get(10).signals()).contains(Signal.THREE_OF_FOUR_BEYOND_HALF);
    assertThat(chart.points().get(11).signals()).contains(Signal.THREE_OF_FOUR_BEYOND_HALF);
    assertThat(chart.points().get(13).signals()).contains(Signal.THREE_OF_FOUR_BEYOND_HALF);
    assertThat(chart.points().get(12).signals()).doesNotContain(Signal.THREE_OF_FOUR_BEYOND_HALF);
  }

  @Test
  void aConstantSeriesHasNoSignals() {
    XmrChart chart = XmrChart.of(List.of(5d, 5d, 5d, 5d, 5d, 5d, 5d, 5d, 5d)).orElseThrow();
    assertThat(chart.upperLimit()).isEqualTo(5);
    assertThat(chart.lowerLimit()).isEqualTo(5);
    assertThat(chart.signalCount()).isZero();
  }

  @Test
  void rejectsNonFiniteValues() {
    assertThatThrownBy(() -> XmrChart.of(List.of(1d, 2d, 3d, 4d, 5d, Double.NaN)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> XmrChart.of(Arrays.asList(1d, 2d, 3d, 4d, 5d, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void pointSignalsAreImmutable() {
    XmrChart chart = XmrChart.of(List.of(1d, 2d, 3d, 4d, 5d, 6d)).orElseThrow();
    assertThatThrownBy(() -> chart.points().getFirst().signals().add(Signal.RUN_OF_EIGHT))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
