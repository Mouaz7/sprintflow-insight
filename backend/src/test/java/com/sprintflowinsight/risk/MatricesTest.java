package com.sprintflowinsight.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class MatricesTest {
  @Test
  void solvesASymmetricPositiveDefiniteSystem() {
    double[][] a = {{4, 2, 0.4}, {2, 5, 1}, {0.4, 1, 3}};
    double[] expected = {1, -2, 0.5};
    double[] b = new double[3];
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++) {
        b[i] += a[i][j] * expected[j];
      }
    }

    double[] x = Matrices.solveSpd(a, b);

    for (int i = 0; i < 3; i++) {
      assertThat(x[i]).isCloseTo(expected[i], within(1e-12));
    }
  }

  @Test
  void reportsMatricesThatAreNotPositiveDefinite() {
    assertThat(Matrices.solveSpd(new double[][] {{1, 1}, {1, 1}}, new double[] {1, 1})).isNull();
    assertThat(Matrices.solveSpd(new double[][] {{-1}}, new double[] {1})).isNull();
    assertThat(Matrices.solveSpd(new double[][] {{Double.NaN}}, new double[] {1})).isNull();
  }

  @Test
  void requiresANonEmptyRectangularFiniteMatrix() {
    assertThat(Matrices.requireRectangular(new double[][] {{1, 2}, {3, 4}})).isEqualTo(2);
    assertThatThrownBy(() -> Matrices.requireRectangular(new double[0][]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Matrices.requireRectangular(new double[][] {{}}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Matrices.requireRectangular(new double[][] {{1, 2}, {3}}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Matrices.requireRectangular(new double[][] {{1, Double.NaN}}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> Matrices.requireRectangular(new double[][] {{Double.NEGATIVE_INFINITY}}))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
