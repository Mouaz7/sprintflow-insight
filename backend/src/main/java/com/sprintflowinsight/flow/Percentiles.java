package com.sprintflowinsight.flow;

import java.util.Arrays;
import java.util.Collection;

/** Sample percentiles by linear interpolation between order statistics (Hyndman–Fan type 7). */
public final class Percentiles {
  private Percentiles() {}

  /**
   * The {@code p}-quantile of {@code values}, with {@code h = (n − 1)p} and linear interpolation
   * between the order statistics around {@code h}. This is the default in R, NumPy and Excel's
   * {@code PERCENTILE.INC}.
   *
   * @param p a probability in [0, 1]
   * @throws IllegalArgumentException for no values, a non-finite value or {@code p} outside [0, 1]
   */
  public static double of(Collection<Double> values, double p) {
    return ofSorted(sorted(values), p);
  }

  /** As {@link #of}, for values already in ascending order. */
  public static double ofSorted(double[] sorted, double p) {
    if (sorted.length == 0) throw new IllegalArgumentException("percentile of no values");
    if (!(p >= 0 && p <= 1)) throw new IllegalArgumentException("p must be in [0, 1]");
    double h = (sorted.length - 1) * p;
    int below = (int) Math.floor(h);
    int above = Math.min(below + 1, sorted.length - 1);
    double fraction = h - below;
    return sorted[below] + fraction * (sorted[above] - sorted[below]);
  }

  /** The values in ascending order; rejects non-finite values. */
  public static double[] sorted(Collection<Double> values) {
    double[] result = new double[values.size()];
    int i = 0;
    for (Double value : values) {
      if (value == null || !Double.isFinite(value))
        throw new IllegalArgumentException("values must be finite");
      result[i++] = value;
    }
    Arrays.sort(result);
    return result;
  }
}
