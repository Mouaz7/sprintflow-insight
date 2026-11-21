package com.sprintflowinsight.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("benchmark")
class EwmaControlChartEvaluationTest {
  private static final long SEED = 20261121;
  private static final int REPLICATIONS = 1000;

  @Test
  void comparesTerminalSignalsUnderStableAndShiftedFlow() throws Exception {
    var random = new Random(SEED);
    var rows =
        new ArrayList<>(List.of("baseline_rate,monitor_rate,replications,low,in_limits,high"));
    for (int baseline : List.of(2, 8, 20)) {
      for (int recent : List.of(baseline / 2, baseline, baseline * 3 / 2)) {
        int[] signals = new int[EwmaControlChart.Signal.values().length];
        for (int run = 0; run < REPLICATIONS; run++) {
          var history = samples(random, baseline, EwmaControlChart.BASELINE_PERIODS);
          var monitoring = samples(random, recent, EwmaControlChart.MONITOR_PERIODS);
          var latest = EwmaControlChart.compare(history, monitoring).monitoring().getLast();
          signals[latest.signal().ordinal()]++;
        }
        if (recent == baseline)
          assertThat(signals[EwmaControlChart.Signal.IN_LIMITS.ordinal()]).isGreaterThan(950);
        rows.add(
            String.join(
                ",",
                Integer.toString(baseline),
                Integer.toString(recent),
                Integer.toString(REPLICATIONS),
                Integer.toString(signals[0]),
                Integer.toString(signals[1]),
                Integer.toString(signals[2])));
      }
    }
    Path directory = Path.of("target", "benchmark");
    Files.createDirectories(directory);
    Files.writeString(directory.resolve("ewma-flow-signals.csv"), String.join("\n", rows) + "\n");
  }

  private static List<Integer> samples(Random random, int rate, int count) {
    double threshold = Math.exp(-rate);
    return IntStream.range(0, count)
        .mapToObj(
            index -> {
              int events = 0;
              double probability = random.nextDouble();
              while (probability > threshold) {
                events++;
                probability *= random.nextDouble();
              }
              return events;
            })
        .toList();
  }
}
