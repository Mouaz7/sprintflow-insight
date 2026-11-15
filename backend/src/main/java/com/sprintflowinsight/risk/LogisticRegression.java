package com.sprintflowinsight.risk;

/**
 * L2-regularised logistic regression, implemented by hand.
 *
 * <p>Minimises {@code J(b, w) = (1/n) Σ [log(1 + e^{z_i}) - y_i z_i] + (λ/2) ||w||²} with {@code
 * z_i = b + w·x_i}. The intercept {@code b} is not penalised. The optimiser is Newton's method
 * (iteratively reweighted least squares) with an Armijo backtracking line search: every accepted
 * step strictly decreases {@code J}, which is strictly convex for {@code λ > 0}, so the iteration
 * cannot oscillate or diverge. Training stops when the largest absolute gradient component is at
 * most {@link Options#tolerance()} ({@link Fit#converged()} = true) or after {@link
 * Options#maxIterations()} steps. No randomness is involved: equal input gives bit-identical
 * output.
 */
public final class LogisticRegression {
  private static final double ARMIJO = 1e-4;
  private static final double MIN_STEP = 1e-12;

  private LogisticRegression() {}

  /**
   * @param lambda L2 penalty on the weights (not the intercept); 0 gives maximum likelihood
   * @param maxIterations maximum number of Newton steps
   * @param tolerance convergence threshold for the largest absolute gradient component
   */
  public record Options(double lambda, int maxIterations, double tolerance) {
    /** λ = 0.01, at most 100 steps, gradient below 1e-8. */
    public static final Options DEFAULT = new Options(0.01, 100, 1e-8);

    public Options {
      if (!(lambda >= 0) || Double.isInfinite(lambda)) {
        throw new IllegalArgumentException("lambda must be finite and non-negative");
      }
      if (maxIterations < 1) {
        throw new IllegalArgumentException("maxIterations must be positive");
      }
      if (!(tolerance > 0)) {
        throw new IllegalArgumentException("tolerance must be positive");
      }
    }
  }

  /**
   * Fitted parameters.
   *
   * @param intercept unpenalised bias
   * @param weights one coefficient per column
   * @param iterations Newton steps taken
   * @param converged whether the gradient criterion was met
   * @param gradientNorm largest absolute gradient component at the returned parameters
   */
  public record Fit(
      double intercept, double[] weights, int iterations, boolean converged, double gradientNorm) {
    public Fit {
      weights = weights.clone();
    }

    @Override
    public double[] weights() {
      return weights.clone();
    }
  }

  /** Fits the model to rows {@code x} (finite, rectangular) and outcomes {@code y}. */
  public static Fit fit(double[][] x, boolean[] y, Options options) {
    int d = Matrices.requireRectangular(x);
    if (y.length != x.length) {
      throw new IllegalArgumentException("x and y differ in length");
    }
    double[] theta = new double[d + 1];
    double objective = objective(x, y, theta, options.lambda());
    int iteration = 0;
    while (true) {
      double[] gradient = gradient(x, y, theta, options.lambda());
      double norm = maxAbs(gradient);
      if (norm <= options.tolerance()) {
        return result(theta, iteration, true, norm);
      }
      if (iteration == options.maxIterations()) {
        return result(theta, iteration, false, norm);
      }
      double[] direction = Matrices.solveSpd(hessian(x, theta, options.lambda()), gradient);
      if (direction == null) {
        direction = gradient;
      }
      double slope = dot(gradient, direction);
      double step = 1.0;
      double[] candidate = null;
      double candidateObjective = objective;
      while (step >= MIN_STEP) {
        candidate = axpy(theta, -step, direction);
        candidateObjective = objective(x, y, candidate, options.lambda());
        if (candidateObjective <= objective - ARMIJO * step * slope) {
          break;
        }
        step /= 2;
      }
      if (step < MIN_STEP) {
        // No descent is possible at machine precision: the optimum has been reached as far as
        // doubles allow.
        return result(theta, iteration, false, norm);
      }
      theta = candidate;
      objective = candidateObjective;
      iteration++;
    }
  }

  /** Numerically stable logistic function. */
  public static double sigmoid(double z) {
    if (z >= 0) {
      return 1.0 / (1.0 + Math.exp(-z));
    }
    double e = Math.exp(z);
    return e / (1.0 + e);
  }

  /** Regularised mean log loss, {@code J(θ)} above, with {@code θ = [b, w...]}. */
  static double objective(double[][] x, boolean[] y, double[] theta, double lambda) {
    double loss = 0;
    for (int i = 0; i < x.length; i++) {
      double z = linear(x[i], theta);
      loss += softplus(z) - (y[i] ? z : 0);
    }
    double penalty = 0;
    for (int j = 1; j < theta.length; j++) {
      penalty += theta[j] * theta[j];
    }
    return loss / x.length + lambda / 2 * penalty;
  }

  static double[] gradient(double[][] x, boolean[] y, double[] theta, double lambda) {
    double[] g = new double[theta.length];
    for (int i = 0; i < x.length; i++) {
      double residual = sigmoid(linear(x[i], theta)) - (y[i] ? 1 : 0);
      g[0] += residual;
      for (int j = 1; j < theta.length; j++) {
        g[j] += residual * x[i][j - 1];
      }
    }
    for (int j = 0; j < g.length; j++) {
      g[j] /= x.length;
    }
    for (int j = 1; j < g.length; j++) {
      g[j] += lambda * theta[j];
    }
    return g;
  }

  private static double[][] hessian(double[][] x, double[] theta, double lambda) {
    int m = theta.length;
    double[][] h = new double[m][m];
    double[] row = new double[m];
    row[0] = 1;
    for (double[] xi : x) {
      double p = sigmoid(linear(xi, theta));
      double s = p * (1 - p);
      System.arraycopy(xi, 0, row, 1, m - 1);
      for (int j = 0; j < m; j++) {
        for (int k = 0; k <= j; k++) {
          h[j][k] += s * row[j] * row[k];
        }
      }
    }
    for (int j = 0; j < m; j++) {
      for (int k = 0; k <= j; k++) {
        h[j][k] /= x.length;
        h[k][j] = h[j][k];
      }
    }
    for (int j = 1; j < m; j++) {
      h[j][j] += lambda;
    }
    return h;
  }

  private static double linear(double[] xi, double[] theta) {
    double z = theta[0];
    for (int j = 0; j < xi.length; j++) {
      z += theta[j + 1] * xi[j];
    }
    return z;
  }

  /** {@code log(1 + e^z)} without overflow. */
  static double softplus(double z) {
    return Math.max(z, 0) + Math.log1p(Math.exp(-Math.abs(z)));
  }

  private static double dot(double[] a, double[] b) {
    double s = 0;
    for (int i = 0; i < a.length; i++) {
      s += a[i] * b[i];
    }
    return s;
  }

  private static double[] axpy(double[] base, double factor, double[] direction) {
    double[] r = new double[base.length];
    for (int i = 0; i < base.length; i++) {
      r[i] = base[i] + factor * direction[i];
    }
    return r;
  }

  private static double maxAbs(double[] v) {
    double m = 0;
    for (double value : v) {
      m = Math.max(m, Math.abs(value));
    }
    return m;
  }

  private static Fit result(double[] theta, int iterations, boolean converged, double norm) {
    double[] weights = new double[theta.length - 1];
    System.arraycopy(theta, 1, weights, 0, weights.length);
    return new Fit(theta[0], weights, iterations, converged, norm);
  }
}
