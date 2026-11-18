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
  void computesIndividualsAndMovingRangeLimits() {
    // moving ranges 2, 2, 2, 2, 2 -> MR̄ = 2; mean = 11
    XmrChart chart = XmrChart.of(List.of(10d, 12d, 10d, 12d, 10d, 12d)).orElseThrow();
    assertThat(chart.mean()).isCloseTo(11, within(1e-12));
    assertThat(chart.movingRangeMean()).isCloseTo(2, within(1e-12));
    assertThat(chart.upperLimit()).isCloseTo(16.319148936170214, within(1e-12));
    assertThat(chart.lowerLimit()).isCloseTo(5.680851063829786, within(1e-12));
    assertThat(chart.upperRangeLimit()).isCloseTo(6.534, within(1e-12));
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
  void matchesTheIndependentNistTenBatchExample() {
    XmrChart chart =
        XmrChart.of(List.of(49.6, 47.6, 49.9, 51.3, 47.8, 51.2, 52.6, 52.4, 53.6, 52.1))
            .orElseThrow();
    assertThat(chart.mean()).isCloseTo(50.81, within(1e-10));
    assertThat(chart.movingRangeMean()).isCloseTo(1.8777777778, within(1e-9));
    assertThat(chart.upperLimit()).isCloseTo(55.8041, within(.0001));
    assertThat(chart.lowerLimit()).isCloseTo(45.8159, within(.0001));
  }

  @Test
  void detectsBothWesternElectricZoneRulesOnBothSides() {
    for (boolean reflected : List.of(false, true)) {
      List<Double> two = new ArrayList<>(), four = new ArrayList<>();
      for (int i = 0; i < 40; i++) {
        two.add(10d + i % 2);
        four.add(10d + i % 2);
      }
      two.addAll(List.of(14d, 14d, 11d));
      four.addAll(List.of(12d, 12d, 10d, 12d, 12d));
      if (reflected) {
        two.replaceAll(x -> 22 - x);
        four.replaceAll(x -> 22 - x);
      }
      XmrChart a = XmrChart.of(two).orElseThrow(), b = XmrChart.of(four).orElseThrow();
      assertThat(a.points().get(40).signals()).contains(Signal.TWO_OF_THREE_BEYOND_TWO_SIGMA);
      assertThat(a.points().get(41).signals()).contains(Signal.TWO_OF_THREE_BEYOND_TWO_SIGMA);
      assertThat(b.points().get(40).signals()).contains(Signal.FOUR_OF_FIVE_BEYOND_ONE_SIGMA);
      assertThat(b.points().get(44).signals()).contains(Signal.FOUR_OF_FIVE_BEYOND_ONE_SIGMA);
      assertThat(b.points().get(42).signals()).doesNotContain(Signal.FOUR_OF_FIVE_BEYOND_ONE_SIGMA);
    }
  }

  @Test
  void aConstantSeriesHasNoSignals() {
    XmrChart chart = XmrChart.of(List.of(5d, 5d, 5d, 5d, 5d, 5d, 5d, 5d, 5d)).orElseThrow();
    assertThat(chart.upperLimit()).isEqualTo(5);
    assertThat(chart.lowerLimit()).isEqualTo(5);
    assertThat(chart.signalCount()).isZero();
    XmrChart decimal = XmrChart.of(java.util.Collections.nCopies(12, 0.1)).orElseThrow();
    assertThat(decimal.mean()).isEqualTo(0.1);
    assertThat(decimal.signalCount()).isZero();
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
