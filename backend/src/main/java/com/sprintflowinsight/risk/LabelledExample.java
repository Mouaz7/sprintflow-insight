package com.sprintflowinsight.risk;

import java.time.Instant;
import java.util.Objects;

/**
 * A historical observation for training and evaluation.
 *
 * @param features the task's features at {@code observedAt}
 * @param late whether the task was not done when that sprint ended
 * @param observedAt when the features were captured; used for time-based splits
 */
public record LabelledExample(RiskFeatures features, boolean late, Instant observedAt) {
  public LabelledExample {
    Objects.requireNonNull(features, "features");
    Objects.requireNonNull(observedAt, "observedAt");
  }
}
