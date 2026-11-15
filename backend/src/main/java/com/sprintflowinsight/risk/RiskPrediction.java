package com.sprintflowinsight.risk;

import java.util.List;

/**
 * Risk for one task.
 *
 * @param probability probability that the task is not done at sprint end, in (0, 1)
 * @param source whether a trained model or the cold-start heuristic produced it
 * @param topReasons at most three non-zero feature contributions, largest absolute value first
 */
public record RiskPrediction(
    double probability, RiskModel.Source source, List<Contribution> topReasons) {
  public RiskPrediction {
    topReasons = List.copyOf(topReasons);
  }

  /**
   * One feature's additive share of the log-odds: coefficient × standardised value. A positive
   * value raises the risk relative to an average task, a negative value lowers it.
   */
  public record Contribution(RiskFeature feature, double logOdds) {
    public boolean raisesRisk() {
      return logOdds > 0;
    }
  }
}
