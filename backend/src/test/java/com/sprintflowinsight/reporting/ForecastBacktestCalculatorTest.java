package com.sprintflowinsight.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.forecast.BacktestReport;
import com.sprintflowinsight.forecast.ThroughputHistory;
import com.sprintflowinsight.reporting.ForecastBacktestResponse.Status;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

class ForecastBacktestCalculatorTest {
  @Test
  void selectsTheLowestCrpsAndForecastsAtTheEvaluatedHorizon() {
    var history = ThroughputHistory.weekly(0, 2, 3, 2, 4, 2, 3, 5, 4, 6, 7, 7, 8, 9, 10, 10);
    var report = ForecastBacktestCalculator.calculate(ForecastBasis.THROUGHPUT, history, 20261120);
    assertThat(report.status()).isEqualTo(Status.SELECTED);
    assertThat(report.selectedModel())
        .isEqualTo(
            report.candidates().stream()
                .min(Comparator.comparingDouble(BacktestReport::meanCrps))
                .orElseThrow()
                .model());
    assertThat(report.prediction().intervals())
        .allSatisfy(
            interval ->
                assertThat(report.prediction().median())
                    .isBetween(interval.lower(), interval.upper()));
    assertThat(ForecastBacktestCalculator.calculate(ForecastBasis.THROUGHPUT, history, 20261120))
        .isEqualTo(report);
  }

  @Test
  void distinguishesMissingHistoryFromNoCompletions() {
    assertThat(
            ForecastBacktestCalculator.calculate(
                ForecastBasis.THROUGHPUT, ThroughputHistory.weekly(0)))
        .extracting(ForecastBacktestResponse::status, ForecastBacktestResponse::prediction)
        .containsExactly(Status.INSUFFICIENT_HISTORY, null);
    assertThat(
            ForecastBacktestCalculator.calculate(
                ForecastBasis.THROUGHPUT, ThroughputHistory.weekly(new int[15])))
        .extracting(ForecastBacktestResponse::status, ForecastBacktestResponse::prediction)
        .containsExactly(Status.NO_COMPLETIONS, null);
  }
}
