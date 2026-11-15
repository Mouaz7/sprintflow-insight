package com.sprintflowinsight.risk;

import java.util.List;

/**
 * Chooses between a trained model and the cold-start heuristic and trains the former.
 *
 * <p>Logistic regression is only fitted when both classes have at least {@link
 * #MIN_EVENTS_PER_CLASS} examples: ten events per candidate feature, the common rule of thumb for
 * stable coefficients (Peduzzi et al., 1996). Below that, {@link ColdStartHeuristic} is used.
 */
public final class RiskModelTrainer {
  /** Minimum count of the rarer outcome (late or on time) before a model is trained. */
  public static final int MIN_EVENTS_PER_CLASS = 10 * RiskFeature.COUNT;

  private final LogisticRegression.Options options;

  public RiskModelTrainer(LogisticRegression.Options options) {
    this.options = options;
  }

  public RiskModelTrainer() {
    this(LogisticRegression.Options.DEFAULT);
  }

  public static boolean hasEnoughHistory(List<LabelledExample> examples) {
    long late = examples.stream().filter(LabelledExample::late).count();
    return Math.min(late, examples.size() - late) >= MIN_EVENTS_PER_CLASS;
  }

  public RiskModel train(List<LabelledExample> examples) {
    if (!hasEnoughHistory(examples)) {
      return ColdStartHeuristic.calibrated(examples);
    }
    double[][] x = new double[examples.size()][];
    boolean[] y = new boolean[examples.size()];
    for (int i = 0; i < x.length; i++) {
      x[i] = examples.get(i).features().toVector();
      y[i] = examples.get(i).late();
    }
    Standardizer standardizer = Standardizer.fit(x);
    LogisticRegression.Fit fit = LogisticRegression.fit(standardizer.transform(x), y, options);
    return new RiskModel(RiskModel.Source.TRAINED, standardizer, fit.intercept(), fit.weights());
  }
}
