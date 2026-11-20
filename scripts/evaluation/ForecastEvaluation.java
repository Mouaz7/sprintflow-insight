package com.sprintflowinsight.reporting;

import com.sprintflowinsight.forecast.ThroughputHistory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Reproduce the comparison from aggregate weekly counts, without accessing Jira. */
public final class ForecastEvaluation {
  private static final long SEED = 20261120L;

  private record Week(LocalDate start, int count) {}

  public static void main(String[] args) throws IOException {
    if (args.length != 1)
      throw new IllegalArgumentException("Provide the aggregate weekly CSV path.");
    var rows = Files.readAllLines(Path.of(args[0]));
    if (rows.isEmpty() || !rows.getFirst().equals("project,week_start_utc,resolved_issues"))
      throw new IllegalArgumentException("Unexpected CSV header.");
    var projects = new TreeMap<String, List<Week>>();
    for (String row : rows.subList(1, rows.size())) {
      String[] cells = row.split(",", -1);
      if (cells.length != 3 || !cells[0].matches("[A-Z][A-Z0-9_]{0,30}"))
        throw new IllegalArgumentException("Invalid aggregate row.");
      var weeks = projects.computeIfAbsent(cells[0], ignored -> new ArrayList<>());
      LocalDate start = LocalDate.parse(cells[1]);
      if (!weeks.isEmpty() && !weeks.getLast().start().plusWeeks(1).equals(start))
        throw new IllegalArgumentException("Weeks must be consecutive and ordered.");
      weeks.add(new Week(start, Integer.parseInt(cells[2])));
    }

    System.out.println(
        "project\tmodel\tselected\tseed\torigins\tcrps\tmae\tcoverage50\tcoverage85\tcoverage95"
            + "\tpit");
    for (var entry : projects.entrySet()) {
      int[] counts = entry.getValue().stream().mapToInt(Week::count).toArray();
      var result =
          ForecastBacktestCalculator.calculate(
              ForecastBasis.THROUGHPUT, ThroughputHistory.weekly(counts), SEED);
      if (result.status() != ForecastBacktestResponse.Status.SELECTED)
        throw new IllegalArgumentException("Not enough usable data for " + entry.getKey());

      for (var report : result.candidates()) {
        String pit =
            report.pitHistogram().stream()
                .map(value -> String.format(Locale.ROOT, "%.6f", value))
                .collect(Collectors.joining(","));
        System.out.printf(
            Locale.ROOT,
            "%s\t%s\t%b\t%d\t%d\t%.6f\t%.6f\t%.6f\t%.6f\t%.6f\t%s%n",
            entry.getKey(),
            report.model(),
            report.model().equals(result.selectedModel()),
            SEED,
            report.origins(),
            report.meanCrps(),
            report.medianAbsoluteError(),
            report.coverage().get(0.50),
            report.coverage().get(0.85),
            report.coverage().get(0.95),
            pit);
      }
    }
  }
}
