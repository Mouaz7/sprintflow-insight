package com.sprintflowinsight.risk;

/**
 * Z-score transformation {@code (x - mean) / scale} per column. {@link #fit} uses the training
 * data only, so no information from the test period leaks into the model. A constant column gets
 * scale 1 and therefore always maps to 0.
 */
public final class Standardizer {
  /** Columns with a smaller standard deviation are treated as constant. */
  static final double MIN_SCALE = 1e-12;

  private final double[] means;
  private final double[] scales;

  private Standardizer(double[] means, double[] scales) {
    this.means = means;
    this.scales = scales;
  }

  /** Means and population standard deviations of each column of a non-empty matrix. */
  public static Standardizer fit(double[][] rows) {
    int width = Matrices.requireRectangular(rows);
    double[] means = new double[width];
    double[] scales = new double[width];
    for (double[] row : rows) {
      for (int j = 0; j < width; j++) {
        means[j] += row[j];
      }
    }
    for (int j = 0; j < width; j++) {
      means[j] /= rows.length;
    }
    for (double[] row : rows) {
      for (int j = 0; j < width; j++) {
        double d = row[j] - means[j];
        scales[j] += d * d;
      }
    }
    for (int j = 0; j < width; j++) {
      double sd = Math.sqrt(scales[j] / rows.length);
      scales[j] = sd < MIN_SCALE ? 1.0 : sd;
    }
    return new Standardizer(means, scales);
  }

  /** A fixed transformation, for example the reference scales of the cold-start heuristic. */
  public static Standardizer of(double[] means, double[] scales) {
    if (means.length != scales.length) {
      throw new IllegalArgumentException("means and scales differ in length");
    }
    for (double scale : scales) {
      if (!(scale > 0) || Double.isInfinite(scale)) {
        throw new IllegalArgumentException("scales must be positive and finite");
      }
    }
    return new Standardizer(means.clone(), scales.clone());
  }

  public double[] transform(double[] row) {
    if (row.length != means.length) {
      throw new IllegalArgumentException("expected " + means.length + " values");
    }
    double[] z = new double[row.length];
    for (int j = 0; j < row.length; j++) {
      z[j] = (row[j] - means[j]) / scales[j];
    }
    return z;
  }

  public double[][] transform(double[][] rows) {
    double[][] z = new double[rows.length][];
    for (int i = 0; i < rows.length; i++) {
      z[i] = transform(rows[i]);
    }
    return z;
  }

  public double[] means() {
    return means.clone();
  }

  public double[] scales() {
    return scales.clone();
  }
}
