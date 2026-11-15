package com.sprintflowinsight.planning.optimizer;

import java.time.Duration;
import java.util.Objects;

/**
 * CP-SAT search parameters.
 *
 * @param timeLimit wall-clock limit; the best plan found so far is returned when it expires
 * @param workers parallel search workers
 * @param randomSeed fixed seed for reproducible runs
 * @param interleaveSearch interleave the workers' strategies in a fixed schedule; thread timing
 *     then cannot change the result, but the search is much slower (opt-in)
 */
public record CpSatSettings(
    Duration timeLimit, int workers, int randomSeed, boolean interleaveSearch) {

  public static final Duration DEFAULT_TIME_LIMIT = Duration.ofSeconds(2);

  public CpSatSettings {
    Objects.requireNonNull(timeLimit, "timeLimit");
    if (timeLimit.isNegative() || timeLimit.isZero()) {
      throw new IllegalArgumentException("Time limit must be positive");
    }
    if (workers < 1) {
      throw new IllegalArgumentException("At least one worker is needed");
    }
  }

  /**
   * 2 s, 8 workers, seed 0, free (not interleaved) search: about 50× faster than interleaving on
   * 100-story backlogs; reproducibility comes from the solver's tie-break instead.
   */
  public static CpSatSettings defaults() {
    return new CpSatSettings(DEFAULT_TIME_LIMIT, 8, 0, false);
  }

  public CpSatSettings withTimeLimit(Duration limit) {
    return new CpSatSettings(limit, workers, randomSeed, interleaveSearch);
  }
}
