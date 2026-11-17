package com.sprintflowinsight.workspace.roadmap.schedule;

/** The standard normal distribution. */
public final class StandardNormal {
  private StandardNormal() {}

  /**
   * {@code P(Z <= z)}, computed as {@code erfc(-z / sqrt 2) / 2} with the Chebyshev-fitted
   * complementary error function from Numerical Recipes ({@code erfcc}); fractional error below
   * 1.2e-7 everywhere, which is far below the precision of any three-point estimate.
   */
  public static double cdf(double z) {
    if (Double.isNaN(z)) throw new IllegalArgumentException("z must be a number");
    if (z == Double.POSITIVE_INFINITY) return 1;
    if (z == Double.NEGATIVE_INFINITY) return 0;
    return 0.5 * erfc(-z / Math.sqrt(2));
  }

  static double erfc(double x) {
    double t = 1 / (1 + 0.5 * Math.abs(x));
    double polynomial =
        -x * x
            - 1.26551223
            + t
                * (1.00002368
                    + t
                        * (0.37409196
                            + t
                                * (0.09678418
                                    + t
                                        * (-0.18628806
                                            + t
                                                * (0.27886807
                                                    + t
                                                        * (-1.13520398
                                                            + t
                                                                * (1.48851587
                                                                    + t
                                                                        * (-0.82215223
                                                                            + t * 0.17087277))))))));
    double value = t * Math.exp(polynomial);
    return x >= 0 ? value : 2 - value;
  }
}
