package com.sprintflowinsight.risk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Evaluation measures for probabilistic binary predictions. */
public final class RiskEvaluation {
  private RiskEvaluation() {}

  /**
   * Exact ROC AUC: the probability that a random positive scores higher than a random negative,
   * with ties counted as one half. Computed from the Mann–Whitney U statistic with mid-ranks in
   * O(n log n). Depends only on the order of the scores, so any strictly increasing transform
   * leaves it unchanged.
   *
   * @throws IllegalArgumentException when either class is missing or a score is not finite
   */
  public static double auc(double[] scores, boolean[] positive) {
    requireSameLength(scores, positive);
    long positives = 0;
    for (int i = 0; i < scores.length; i++) {
      requireFinite(scores[i]);
      if (positive[i]) {
        positives++;
      }
    }
    long negatives = scores.length - positives;
    if (positives == 0 || negatives == 0) {
      throw new IllegalArgumentException("AUC needs both positive and negative examples");
    }
    Integer[] order = new Integer[scores.length];
    Arrays.setAll(order, i -> i);
    Arrays.sort(order, Comparator.comparingDouble(i -> scores[i]));
    double positiveRankSum = 0;
    int start = 0;
    while (start < order.length) {
      int end = start;
      while (end + 1 < order.length && scores[order[end + 1]] == scores[order[start]]) {
        end++;
      }
      // Ranks are 1-based; a tie group shares the mean of its ranks.
      double midRank = (start + end) / 2.0 + 1;
      for (int k = start; k <= end; k++) {
        if (positive[order[k]]) {
          positiveRankSum += midRank;
        }
      }
      start = end + 1;
    }
    double u = positiveRankSum - positives * (positives + 1) / 2.0;
    return u / ((double) positives * negatives);
  }

  /** Brier score: mean squared difference between probability and outcome (0 is perfect). */
  public static double brier(double[] probabilities, boolean[] outcomes) {
    requireSameLength(probabilities, outcomes);
    if (probabilities.length == 0) {
      throw new IllegalArgumentException("Brier score of no predictions");
    }
    double sum = 0;
    for (int i = 0; i < probabilities.length; i++) {
      requireProbability(probabilities[i]);
      double d = probabilities[i] - (outcomes[i] ? 1 : 0);
      sum += d * d;
    }
    return sum / probabilities.length;
  }

  /**
   * One bin of a reliability diagram.
   *
   * @param lower inclusive lower probability bound
   * @param upper exclusive upper bound (inclusive for the last bin)
   * @param count predictions in the bin
   * @param meanPredicted mean predicted probability in the bin
   * @param observedRate share of positive outcomes in the bin
   */
  public record CalibrationBin(
      double lower, double upper, int count, double meanPredicted, double observedRate) {}

  /**
   * Reliability curve with {@code bins} equal-width probability bins. Empty bins are omitted. A
   * well-calibrated model has {@code meanPredicted ≈ observedRate} in every bin.
   */
  public static List<CalibrationBin> calibrationCurve(
      double[] probabilities, boolean[] outcomes, int bins) {
    requireSameLength(probabilities, outcomes);
    if (bins < 1) {
      throw new IllegalArgumentException("bins must be positive");
    }
    int[] count = new int[bins];
    double[] predicted = new double[bins];
    double[] smallest = new double[bins];
    double[] largest = new double[bins];
    int[] positives = new int[bins];
    for (int i = 0; i < probabilities.length; i++) {
      double p = probabilities[i];
      requireProbability(p);
      int bin = binOf(p, bins);
      smallest[bin] = count[bin] == 0 ? p : Math.min(smallest[bin], p);
      largest[bin] = count[bin] == 0 ? p : Math.max(largest[bin], p);
      count[bin]++;
      predicted[bin] += p;
      if (outcomes[i]) {
        positives[bin]++;
      }
    }
    List<CalibrationBin> curve = new ArrayList<>();
    for (int b = 0; b < bins; b++) {
      if (count[b] > 0) {
        curve.add(
            new CalibrationBin(
                lowerBound(b, bins),
                lowerBound(b + 1, bins),
                count[b],
                // A rounded sum can put the mean just outside the values (3 × 0.7 / 3 <
                // 0.7); the exact mean always lies between the smallest and largest value.
                Math.clamp(predicted[b] / count[b], smallest[b], largest[b]),
                (double) positives[b] / count[b]));
      }
    }
    return curve;
  }

  /**
   * The bin {@code b} with {@code b/bins <= p < (b+1)/bins} (the last bin also holds 1). {@code
   * p * bins} can round across a boundary, for example {@code Math.nextDown(0.7) * 10 == 7.0}, so
   * the candidate is checked against the same bounds that are reported.
   */
  static int binOf(double p, int bins) {
    int bin = Math.min(bins - 1, (int) Math.floor(p * bins));
    if (bin > 0 && p < lowerBound(bin, bins)) {
      bin--;
    } else if (bin < bins - 1 && p >= lowerBound(bin + 1, bins)) {
      bin++;
    }
    return bin;
  }

  private static double lowerBound(int bin, int bins) {
    return (double) bin / bins;
  }

  /** Training and test sets of a time-based split. */
  public record TimeSplit(List<LabelledExample> train, List<LabelledExample> test) {
    public TimeSplit {
      train = List.copyOf(train);
      test = List.copyOf(test);
    }
  }

  /**
   * Splits chronologically: the earliest {@code trainFraction} of the examples train, the rest
   * test, so the model never sees the future it is evaluated on. Examples sharing the boundary
   * timestamp all go to the test set; every training example is therefore strictly older than
   * every test example. Equal timestamps keep their input order.
   */
  public static TimeSplit timeSplit(List<LabelledExample> examples, double trainFraction) {
    if (!(trainFraction > 0 && trainFraction < 1)) {
      throw new IllegalArgumentException("trainFraction must be in (0, 1)");
    }
    List<LabelledExample> sorted = new ArrayList<>(examples);
    sorted.sort(Comparator.comparing(LabelledExample::observedAt));
    int cut = (int) Math.floor(trainFraction * sorted.size());
    while (cut > 0
        && cut < sorted.size()
        && sorted.get(cut - 1).observedAt().equals(sorted.get(cut).observedAt())) {
      cut--;
    }
    return new TimeSplit(sorted.subList(0, cut), sorted.subList(cut, sorted.size()));
  }

  private static void requireSameLength(double[] values, boolean[] labels) {
    if (values.length != labels.length) {
      throw new IllegalArgumentException("predictions and outcomes differ in length");
    }
  }

  private static void requireFinite(double value) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("scores must be finite");
    }
  }

  private static void requireProbability(double p) {
    if (!(p >= 0 && p <= 1)) {
      throw new IllegalArgumentException("probabilities must be in [0, 1]");
    }
  }
}
