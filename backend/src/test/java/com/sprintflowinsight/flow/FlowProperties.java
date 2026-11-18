package com.sprintflowinsight.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

class FlowProperties {
  @Property(tries = 150, seed = "2026111705")
  void flowCountsAndTimeAveragesMatchAnIndependentHourlyLedger(@ForAll int seed) {
    Random random = new Random(seed);
    Instant first = Instant.parse("2026-01-01T00:00:00Z");
    List<FlowAnalysis.Item> items = new ArrayList<>();
    int[][] ledger = new int[25][72];
    long[] statusHours = new long[4];
    for (int task = 0; task < ledger.length; task++) {
      List<Transition> rows = new ArrayList<>();
      for (int hour = 0; hour < 72; hour++) {
        int state = hour == 0 ? 0 : random.nextInt(4);
        ledger[task][hour] = state;
        statusHours[state]++;
        rows.add(new Transition(FlowStatus.ALL.get(state), first.plusSeconds(hour * 3600L)));
      }
      items.add(new FlowAnalysis.Item(task, TaskTimeline.of(first, rows)));
    }
    var report =
        FlowAnalysis.analyse(
            items,
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-01-03"),
            first.plusSeconds(72 * 3600),
            Map.of());
    for (int day = 0; day < 3; day++) {
      int[] expected = new int[4];
      for (int[] task : ledger) expected[task[(day + 1) * 24 - 1]]++;
      for (int state = 0; state < 4; state++)
        assertThat(report.cumulativeFlow().get(day).counts().get(FlowStatus.ALL.get(state)))
            .isEqualTo(expected[state]);
    }
    for (int state = 0; state < 3; state++)
      assertThat(report.columns().get(state).averageWip())
          .isCloseTo(statusHours[state] / 72d, within(1e-10));
    assertThat(report.littlesLaw().averageWip())
        .isCloseTo((statusHours[1] + statusHours[2]) / 72d, within(1e-10));
  }

  @Property(tries = 150, seed = "2026111706")
  void xmrSignalsAreInvariantToPositiveChangesOfUnitsAndOrigin(@ForAll int seed) {
    Random random = new Random(seed);
    List<Double> values = new ArrayList<>();
    for (int i = 0; i < 36; i++) values.add((double) random.nextInt(100));
    double scale = 1 + random.nextInt(5), offset = random.nextInt(100);
    var original = XmrChart.of(values).orElseThrow();
    var transformed =
        XmrChart.of(values.stream().map(x -> x * scale + offset).toList()).orElseThrow();
    assertThat(transformed.mean()).isCloseTo(original.mean() * scale + offset, within(1e-9));
    for (int i = 0; i < values.size(); i++)
      assertThat(transformed.points().get(i).signals())
          .isEqualTo(original.points().get(i).signals());
  }
}
