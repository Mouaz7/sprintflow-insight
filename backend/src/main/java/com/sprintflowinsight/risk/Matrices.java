package com.sprintflowinsight.risk;

/** Small dense linear-algebra helpers; dimensions here are at most a dozen. */
final class Matrices {
  private Matrices() {}

  /** Returns the common row length of a non-empty matrix with at least one column. */
  static int requireRectangular(double[][] rows) {
    if (rows.length == 0 || rows[0].length == 0) {
      throw new IllegalArgumentException("matrix must be non-empty");
    }
    int width = rows[0].length;
    for (double[] row : rows) {
      if (row.length != width) {
        throw new IllegalArgumentException("rows differ in length");
      }
      for (double v : row) {
        if (!Double.isFinite(v)) {
          throw new IllegalArgumentException("matrix values must be finite");
        }
      }
    }
    return width;
  }

  /**
   * Solves {@code a x = b} for a symmetric positive-definite {@code a} by Cholesky decomposition.
   * Returns {@code null} when {@code a} is not numerically positive definite.
   */
  static double[] solveSpd(double[][] a, double[] b) {
    int n = b.length;
    double[][] l = new double[n][n];
    for (int i = 0; i < n; i++) {
      for (int j = 0; j <= i; j++) {
        double sum = a[i][j];
        for (int k = 0; k < j; k++) {
          sum -= l[i][k] * l[j][k];
        }
        if (i == j) {
          if (!(sum > 0)) {
            return null;
          }
          l[i][i] = Math.sqrt(sum);
        } else {
          l[i][j] = sum / l[j][j];
        }
      }
    }
    double[] y = new double[n];
    for (int i = 0; i < n; i++) {
      double sum = b[i];
      for (int k = 0; k < i; k++) {
        sum -= l[i][k] * y[k];
      }
      y[i] = sum / l[i][i];
    }
    double[] x = new double[n];
    for (int i = n - 1; i >= 0; i--) {
      double sum = y[i];
      for (int k = i + 1; k < n; k++) {
        sum -= l[k][i] * x[k];
      }
      x[i] = sum / l[i][i];
    }
    return x;
  }
}
