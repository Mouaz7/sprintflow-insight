package com.sprintflowinsight.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class MonteCarloSimulationTest {
  @Test
  void reproducesEveryTwoDayBinaryPathAndExactTailProbabilities() {
    var result =
        MonteCarloSimulation.simulate(
            ThroughputHistory.of(1, ThroughputUnit.ITEMS, 0, 2),
            1,
            2,
            2,
            sequence(0, 0, 0, 1, 1, 0, 1, 1),
            4,
            2);
    assertThat(result.targetProbability()).isEqualTo(0.75);
    assertThat(result.completions())
        .containsExactly(
            new MonteCarloSimulation.Completion(0.5, 1),
            new MonteCarloSimulation.Completion(0.85, null),
            new MonteCarloSimulation.Completion(0.95, null));
    assertThat(result.quantities())
        .containsExactly(
            new MonteCarloSimulation.Quantity(0.5, 2),
            new MonteCarloSimulation.Quantity(0.85, 0),
            new MonteCarloSimulation.Quantity(0.95, 0));
    assertThat(result.beyondHorizon()).isEqualTo(1);
    assertThat(result.completionHistogram())
        .containsExactly(
            new MonteCarloSimulation.Bin(1, 1, 2), new MonteCarloSimulation.Bin(2, 2, 1));
    assertThat(result.quantityHistogram().stream().mapToInt(MonteCarloSimulation.Bin::count).sum())
        .isEqualTo(4);
  }

  @Test
  void constantCapacityHasKnownDatesAndUniformPartialPeriodAccrual() {
    var result =
        MonteCarloSimulation.simulate(
            ThroughputHistory.weekly(7), 10, 12, 5, new SplittableRandom(15));
    assertThat(result.trials()).isEqualTo(10_000);
    assertThat(result.completions()).allMatch(row -> row.days() == 10);
    assertThat(result.quantities()).allMatch(row -> row.items() == 5);
    assertThat(result.targetProbability()).isZero();
    assertThat(result.beyondHorizon()).isZero();
    assertThat(result.completionHistogram())
        .containsExactly(new MonteCarloSimulation.Bin(10, 10, 10_000));
  }

  @Test
  void answersSharePathsAndAreReproducibleAcrossQuestionChanges() {
    var history = ThroughputHistory.weekly(0, 2, 4, 8);
    var first =
        MonteCarloSimulation.simulate(history, 5, 3, 14, new SplittableRandom(16), 1000, 56);
    var later =
        MonteCarloSimulation.simulate(history, 5, 3, 28, new SplittableRandom(16), 1000, 56);
    var larger =
        MonteCarloSimulation.simulate(history, 15, 3, 14, new SplittableRandom(16), 1000, 56);
    assertThat(later.completions()).isEqualTo(first.completions());
    assertThat(later.targetProbability()).isGreaterThanOrEqualTo(first.targetProbability());
    assertThat(larger.quantities()).isEqualTo(first.quantities());
    assertThat(larger.quantityHistogram()).isEqualTo(first.quantityHistogram());
    for (int i = 0; i < 3; i++) {
      Integer a = first.completions().get(i).days(), b = larger.completions().get(i).days();
      assertThat(b == null || a != null && b >= a).isTrue();
    }
    assertThat(MonteCarloSimulation.simulate(history, 5, 3, 14, new SplittableRandom(16), 1000, 56))
        .isEqualTo(first);
  }

  @Test
  void unavailableProgressAndTrivialTargetsAreDistinguished() {
    for (var history :
        new ThroughputHistory[] {ThroughputHistory.weekly(), ThroughputHistory.weekly(0, 0)}) {
      var result =
          MonteCarloSimulation.simulate(history, 1, 1, 5, new SplittableRandom(17), 20, 31);
      assertThat(result.completions()).allMatch(row -> row.days() == null);
      assertThat(result.beyondHorizon()).isEqualTo(20);
      assertThat(result.completionHistogram()).isEmpty();
      assertThat(result.targetProbability()).isZero();
      var trivial =
          MonteCarloSimulation.simulate(history, 0, 0, 0, new SplittableRandom(17), 20, 31);
      assertThat(trivial.completions()).allMatch(row -> row.days() == 0);
      assertThat(trivial.targetProbability()).isEqualTo(1);
    }
  }

  @Test
  void clipsTheLastPeriodToTheHorizonAndNeverInventsACompletionBeyondIt() {
    var result =
        MonteCarloSimulation.simulate(
            ThroughputHistory.weekly(1), 5, 5, 31, new SplittableRandom(18), 100, 31);
    assertThat(result.beyondHorizon()).isEqualTo(100);
    assertThat(result.quantities()).allMatch(row -> row.items() == 4);
    assertThat(result.targetProbability()).isZero();
  }

  @Test
  void largeWorkRangesHaveBoundedHistogramsWithoutIntegerOverflow() {
    var result =
        MonteCarloSimulation.simulate(
            ThroughputHistory.of(1, ThroughputUnit.STORY_POINTS, 0, Integer.MAX_VALUE),
            1,
            1,
            10,
            new SplittableRandom(19),
            1000,
            10);
    assertThat(result.quantityHistogram()).hasSizeLessThanOrEqualTo(20);
    assertThat(result.quantityHistogram().getLast().to()).isGreaterThan(Integer.MAX_VALUE);
    assertThat(result.quantityHistogram().stream().mapToInt(MonteCarloSimulation.Bin::count).sum())
        .isEqualTo(1000);
  }

  @Test
  void validatesBoundsBeforeAllocatingOrSampling() {
    var history = ThroughputHistory.weekly(1);
    assertThatThrownBy(
            () -> MonteCarloSimulation.simulate(history, -1, 0, 0, new SplittableRandom()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> MonteCarloSimulation.simulate(history, 0, -1, 0, new SplittableRandom()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> MonteCarloSimulation.simulate(history, 0, 0, 1826, new SplittableRandom()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MonteCarloSimulation.simulate(history, 0, 0, 0, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MonteCarloSimulation.simulate(null, 0, 0, 0, new SplittableRandom()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static Random sequence(int... values) {
    return new Random() {
      private int cursor;

      @Override
      public int nextInt(int bound) {
        return values[cursor++ % values.length];
      }
    };
  }
}
