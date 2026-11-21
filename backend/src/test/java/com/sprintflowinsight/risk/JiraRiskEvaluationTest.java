package com.sprintflowinsight.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("benchmark")
class JiraRiskEvaluationTest {
  private static final LocalDate CUTOFF = LocalDate.of(2021, 1, 1);

  record Observation(
      String project, LocalDate observed, LocalDate outcome, LabelledExample example) {}

  @Test
  void evaluatesPredictionsAgainstPreviouslyUnseenSprintOutcomes() throws Exception {
    var input = Path.of("..", "docs", "evaluation", "apache-jira-risk.csv");
    var rows =
        Files.readAllLines(input).stream().skip(1).map(JiraRiskEvaluationTest::parse).toList();
    assertThat(rows).isNotEmpty();
    var summaries =
        new ArrayList<>(List.of("scope,model,training,training_late,test,test_late,auc,brier"));
    var curves =
        new ArrayList<>(List.of("scope,model,lower,upper,count,mean_predicted,observed_rate"));
    var scopes = new TreeSet<String>();
    for (var row : rows) scopes.add(row.project());
    scopes.add("POOLED");

    for (String scope : scopes) {
      var pool =
          rows.stream()
              .filter(row -> scope.equals("POOLED") || scope.equals(row.project()))
              .toList();
      List<LabelledExample> training =
          pool.stream()
              .filter(row -> row.outcome().isBefore(CUTOFF))
              .map(Observation::example)
              .toList();
      List<LabelledExample> test =
          pool.stream()
              .filter(row -> !row.observed().isBefore(CUTOFF))
              .map(Observation::example)
              .toList();
      if (test.isEmpty()) continue;
      long late = training.stream().filter(LabelledExample::late).count();
      RiskModel selected = new RiskModelTrainer().train(training);
      record Candidate(String name, ToDoubleFunction<RiskFeatures> score) {}
      var methods =
          List.of(
              new Candidate("NIGHTLY_" + selected.source(), selected::probability),
              new Candidate("HEURISTIC", ColdStartHeuristic.calibrated(training)::probability),
              new Candidate("BASE_RATE", ignored -> (late + 1.0) / (training.size() + 4.0)));
      for (var method : methods) {
        double[] predictions =
            test.stream().map(LabelledExample::features).mapToDouble(method.score()).toArray();
        boolean[] outcomes = new boolean[test.size()];
        for (int i = 0; i < test.size(); i++) outcomes[i] = test.get(i).late();
        long testLate = test.stream().filter(LabelledExample::late).count();
        String auc =
            testLate == 0 || testLate == test.size()
                ? ""
                : String.format(Locale.ROOT, "%.6f", RiskEvaluation.auc(predictions, outcomes));
        summaries.add(
            String.format(
                Locale.ROOT,
                "%s,%s,%d,%d,%d,%d,%s,%.6f",
                scope,
                method.name(),
                training.size(),
                late,
                test.size(),
                testLate,
                auc,
                RiskEvaluation.brier(predictions, outcomes)));
        for (var bin : RiskEvaluation.calibrationCurve(predictions, outcomes, 10)) {
          curves.add(
              String.format(
                  Locale.ROOT,
                  "%s,%s,%.1f,%.1f,%d,%.6f,%.6f",
                  scope,
                  method.name(),
                  bin.lower(),
                  bin.upper(),
                  bin.count(),
                  bin.meanPredicted(),
                  bin.observedRate()));
        }
      }
    }
    assertThat(summaries).hasSizeGreaterThan(1);
    var output = Path.of("target", "benchmark");
    Files.createDirectories(output);
    Files.writeString(
        output.resolve("apache-jira-risk-metrics.csv"), String.join("\n", summaries) + "\n");
    Files.writeString(
        output.resolve("apache-jira-risk-calibration.csv"), String.join("\n", curves) + "\n");
  }

  private static Observation parse(String line) {
    String[] row = line.split(",", -1);
    LocalDate date = LocalDate.parse(row[1]);
    LocalDate cutoff = LocalDate.parse(row[2]);
    assertThat(date).isBeforeOrEqualTo(cutoff);
    double[] f = Arrays.stream(row, 4, 12).mapToDouble(Double::parseDouble).toArray();
    var features =
        new RiskFeatures(
            f[0], (int) f[1], (int) f[2], (int) f[3], f[4], (int) f[5], f[6], (int) f[7]);
    var example =
        new LabelledExample(
            features, "1".equals(row[3]), date.atStartOfDay().toInstant(ZoneOffset.UTC));
    return new Observation(row[0], date, cutoff, example);
  }
}
