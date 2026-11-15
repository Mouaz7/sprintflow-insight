package com.sprintflowinsight.risk;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Synthetic, seeded data for the risk tests. */
final class Fixtures {
  /** Ground-truth log-odds weights per raw feature unit used by {@link #history}. */
  static final double[] TRUE_WEIGHTS = {1.2, 0.5, 0.3, 0.05, 0.15, 0.4, -0.35, 0.1};

  static final double TRUE_INTERCEPT = -1.0;

  private Fixtures() {}

  static RiskFeatures randomFeatures(Random random) {
    return new RiskFeatures(
        random.nextDouble() * 3,
        random.nextInt(4),
        random.nextInt(3),
        random.nextInt(10),
        random.nextInt(9),
        random.nextInt(4),
        random.nextDouble() * 10,
        1 + random.nextInt(8));
  }

  /** {@code n} examples drawn from a logistic model, one per hour starting at the epoch. */
  static List<LabelledExample> history(int n, long seed) {
    Random random = new Random(seed);
    List<LabelledExample> examples = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      RiskFeatures features = randomFeatures(random);
      double z = TRUE_INTERCEPT;
      double[] v = features.toVector();
      for (int j = 0; j < v.length; j++) {
        z += TRUE_WEIGHTS[j] * v[j];
      }
      boolean late = random.nextDouble() < LogisticRegression.sigmoid(z);
      examples.add(new LabelledExample(features, late, Instant.EPOCH.plusSeconds(3600L * i)));
    }
    return examples;
  }

  /** {@code n} rows of {@code d} standard-normal columns and outcomes from the given model. */
  static Data logistic(int n, double intercept, double[] weights, long seed) {
    Random random = new Random(seed);
    double[][] x = new double[n][weights.length];
    boolean[] y = new boolean[n];
    for (int i = 0; i < n; i++) {
      double z = intercept;
      for (int j = 0; j < weights.length; j++) {
        x[i][j] = random.nextGaussian();
        z += weights[j] * x[i][j];
      }
      y[i] = random.nextDouble() < LogisticRegression.sigmoid(z);
    }
    return new Data(x, y);
  }

  record Data(double[][] x, boolean[] y) {}

  static double norm(double[] v) {
    double s = 0;
    for (double value : v) {
      s += value * value;
    }
    return Math.sqrt(s);
  }

  static RiskFeatures reference() {
    return new RiskFeatures(1, 0, 0, 0, 3, 0, 5, 3);
  }
}
