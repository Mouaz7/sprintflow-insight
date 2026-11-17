package com.sprintflowinsight.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.sprintflowinsight.flow.FlowAnalysis.AgeBand;
import com.sprintflowinsight.flow.FlowAnalysis.BottleneckReason;
import com.sprintflowinsight.flow.FlowAnalysis.Column;
import com.sprintflowinsight.flow.FlowAnalysis.Item;
import com.sprintflowinsight.flow.FlowAnalysis.Report;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** One fixed project history with hand-computed expectations. */
class FlowAnalysisTest {
  private static final LocalDate START = LocalDate.parse("2026-03-01");
  private static final LocalDate END = LocalDate.parse("2026-03-10");
  private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");

  private static Transition t(String status, String at) {
    return new Transition(status, Instant.parse(at));
  }

  private static Item item(long id, String created, Transition... transitions) {
    return new Item(id, TaskTimeline.of(Instant.parse(created), List.of(transitions)));
  }

  static List<Item> fixture() {
    return List.of(
        item(
            1,
            "2026-03-01T00:00:00Z",
            t("To Do", "2026-03-01T00:00:00Z"),
            t("In Progress", "2026-03-02T00:00:00Z"),
            t("Done", "2026-03-03T00:00:00Z")),
        item(
            2,
            "2026-03-01T00:00:00Z",
            t("To Do", "2026-03-01T00:00:00Z"),
            t("In Progress", "2026-03-02T00:00:00Z"),
            t("Review", "2026-03-04T00:00:00Z"),
            t("Done", "2026-03-05T00:00:00Z")),
        item(
            3,
            "2026-03-03T00:00:00Z",
            t("To Do", "2026-03-03T00:00:00Z"),
            t("In Progress", "2026-03-08T00:00:00Z")),
        item(4, "2026-03-01T00:00:00Z", t("To Do", "2026-03-01T00:00:00Z")),
        item(5, "2026-03-01T00:00:00Z"),
        item(
            6,
            "2026-02-01T00:00:00Z",
            t("To Do", "2026-02-01T00:00:00Z"),
            t("In Progress", "2026-02-02T00:00:00Z"),
            t("Done", "2026-02-03T00:00:00Z")));
  }

  private static Report report(Map<String, Integer> limits) {
    return FlowAnalysis.analyse(fixture(), START, END, NOW, limits);
  }

  @Test
  void cumulativeFlowCountsEachColumnAtTheEndOfEveryDay() {
    Report report = report(Map.of());
    assertThat(report.cumulativeFlow()).hasSize(10);
    assertThat(report.cumulativeFlow().getFirst().counts())
        .containsExactlyEntriesOf(
            Map.of("To Do", 3, "In Progress", 0, "Review", 0, "Done", 1).entrySet().stream()
                .collect(
                    java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a,
                        java.util.LinkedHashMap::new)))
        .containsEntry("To Do", 3);
    assertThat(report.cumulativeFlow().get(1).counts())
        .containsEntry("To Do", 1)
        .containsEntry("In Progress", 2)
        .containsEntry("Done", 1);
    assertThat(report.cumulativeFlow().get(3).counts()).containsEntry("Review", 1);
    assertThat(report.cumulativeFlow().getLast().date()).isEqualTo(END);
    assertThat(report.cumulativeFlow().getLast().counts())
        .containsEntry("To Do", 1)
        .containsEntry("In Progress", 1)
        .containsEntry("Review", 0)
        .containsEntry("Done", 3);
    assertThat(report.tasksWithoutHistory()).isEqualTo(1);
  }

  @Test
  void percentilesCoverCompletionsInsideTheWindowOnly() {
    Report report = report(Map.of());
    assertThat(report.completions()).extracting(FlowAnalysis.CompletedItem::taskId)
        .containsExactly(1L, 2L);
    assertThat(report.cycleTime().count()).isEqualTo(2);
    assertThat(report.cycleTime().mean()).isCloseTo(48, within(1e-9));
    assertThat(report.cycleTime().p50()).isCloseTo(48, within(1e-9));
    assertThat(report.cycleTime().p85()).isCloseTo(64.8, within(1e-9));
    assertThat(report.cycleTime().p95()).isCloseTo(69.6, within(1e-9));
    assertThat(report.leadTime().p50()).isCloseTo(72, within(1e-9));
    assertThat(report.throughputPerDay()).isCloseTo(2 / 9.5, within(1e-9));
    assertThat(report.controlChart()).isEmpty();
  }

  @Test
  void agingWipIsComparedWithTheCyclePercentiles() {
    Report report = report(Map.of());
    assertThat(report.aging()).hasSize(1);
    FlowAnalysis.AgingItem aging = report.aging().getFirst();
    assertThat(aging.taskId()).isEqualTo(3);
    assertThat(aging.status()).isEqualTo("In Progress");
    assertThat(aging.ageHours()).isCloseTo(60, within(1e-9));
    assertThat(aging.statusAgeHours()).isCloseTo(60, within(1e-9));
    assertThat(aging.band()).isEqualTo(AgeBand.P50_TO_P85);
  }

  @Test
  void columnsReportLimitsAverageWipAndTheBottleneck() {
    Report report = report(Map.of("In Progress", 1, "To Do", 5));
    assertThat(report.columns()).extracting(Column::status)
        .containsExactly("To Do", "In Progress", "Review");
    Column inProgress = report.columns().get(1);
    assertThat(inProgress.currentWip()).isEqualTo(1);
    assertThat(inProgress.limit()).isEqualTo(1);
    assertThat(inProgress.overLimit()).isFalse();
    assertThat(inProgress.averageWip()).isCloseTo(5.5 / 9.5, within(1e-9));
    assertThat(inProgress.bottleneck()).isEqualTo(BottleneckReason.HIGHEST_AVERAGE_WIP);
    assertThat(report.columns().get(2).averageWip()).isCloseTo(1 / 9.5, within(1e-9));
    assertThat(report.columns().get(2).limit()).isNull();
    assertThat(report.columns().get(0).bottleneck()).isNull();
  }

  @Test
  void littlesLawNeedsEnoughCompletions() {
    LittlesLaw law = report(Map.of()).littlesLaw();
    assertThat(law.status()).isEqualTo(LittlesLaw.Status.INSUFFICIENT_DATA);
    assertThat(law.averageWip()).isCloseTo(6.5 / 9.5, within(1e-9));
    assertThat(law.averageCycleTimeDays()).isCloseTo(2, within(1e-9));
  }

  @Test
  void aSteadyFlowSatisfiesLittlesLawAndGetsAControlChart() {
    // One task starts every day and takes exactly two days: L = 2, λ = 1/day, W = 2 days.
    List<Item> items = new ArrayList<>();
    LocalDate first = LocalDate.parse("2026-01-01");
    for (int day = 0; day < 80; day++) {
      Instant start = first.plusDays(day).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
      List<Transition> transitions = new ArrayList<>();
      transitions.add(new Transition("To Do", start));
      transitions.add(new Transition("In Progress", start));
      Instant done = start.plusSeconds(2 * 86_400);
      if (done.isBefore(Instant.parse("2026-03-20T00:00:00Z")))
        transitions.add(new Transition("Done", done));
      items.add(new Item(day, TaskTimeline.of(start, transitions)));
    }
    Report report =
        FlowAnalysis.analyse(
            items,
            LocalDate.parse("2026-02-01"),
            LocalDate.parse("2026-03-01"),
            Instant.parse("2026-03-20T00:00:00Z"),
            Map.of());
    assertThat(report.littlesLaw().status()).isEqualTo(LittlesLaw.Status.CONSISTENT);
    assertThat(report.littlesLaw().ratio()).isCloseTo(1, within(0.05));
    assertThat(report.controlChart()).isPresent();
    assertThat(report.controlChart().orElseThrow().signalCount()).isZero();
  }

  @Test
  void anOverLimitActiveColumnIsTheBottleneck() {
    List<Column> columns =
        List.of(
            new Column("To Do", 9, 5, 2, true, null),
            new Column("In Progress", 3, 3, 2, true, null),
            new Column("Review", 4, 1, 2, true, null));
    List<Column> marked = FlowAnalysis.markBottleneck(columns);
    assertThat(marked.get(0).bottleneck()).isNull();
    assertThat(marked.get(1).bottleneck()).isNull();
    assertThat(marked.get(2).bottleneck()).isEqualTo(BottleneckReason.OVER_LIMIT);
  }

  @Test
  void noActiveWorkMeansNoBottleneck() {
    List<Column> columns =
        List.of(
            new Column("To Do", 3, 3, null, false, null),
            new Column("In Progress", 0, 0, null, false, null),
            new Column("Review", 0, 0, null, false, null));
    assertThat(FlowAnalysis.markBottleneck(columns)).extracting(Column::bottleneck)
        .containsOnlyNulls();
  }

  @Test
  void agesWithoutHistoryHaveNoReference() {
    FlowAnalysis.Distribution none = new FlowAnalysis.Distribution(0, null, null, null, null);
    assertThat(FlowAnalysis.band(5, none)).isEqualTo(AgeBand.NO_REFERENCE);
    FlowAnalysis.Distribution some = new FlowAnalysis.Distribution(3, 10d, 10d, 20d, 30d);
    assertThat(FlowAnalysis.band(10, some)).isEqualTo(AgeBand.BELOW_P50);
    assertThat(FlowAnalysis.band(20, some)).isEqualTo(AgeBand.P50_TO_P85);
    assertThat(FlowAnalysis.band(30, some)).isEqualTo(AgeBand.P85_TO_P95);
    assertThat(FlowAnalysis.band(30.1, some)).isEqualTo(AgeBand.ABOVE_P95);
  }

  @Test
  void theWindowStopsAtNowAndMustNotBeReversed() {
    Report report =
        FlowAnalysis.analyse(
            fixture(), START, LocalDate.parse("2026-03-20"), NOW, Map.of());
    assertThat(report.cumulativeFlow().getLast().date()).isEqualTo(END);
    assertThatThrownBy(() -> FlowAnalysis.analyse(fixture(), END, START, NOW, Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
