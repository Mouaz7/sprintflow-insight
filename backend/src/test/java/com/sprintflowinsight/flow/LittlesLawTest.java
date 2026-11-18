package com.sprintflowinsight.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.sprintflowinsight.flow.LittlesLaw.Status;
import org.junit.jupiter.api.Test;

class LittlesLawTest {
  @Test
  void anExactMatchIsConsistent() {
    LittlesLaw check = LittlesLaw.check(6, 2, 3, 20, 0.25);
    assertThat(check.status()).isEqualTo(Status.CONSISTENT);
    assertThat(check.expectedWip()).isEqualTo(6);
    assertThat(check.ratio()).isCloseTo(1, within(1e-12));
  }

  @Test
  void theToleranceIsSymmetricOnARatioScale() {
    assertThat(LittlesLaw.check(7.5, 2, 3, 20, 0.25).status()).isEqualTo(Status.CONSISTENT);
    assertThat(LittlesLaw.check(4.81, 2, 3, 20, 0.25).status()).isEqualTo(Status.CONSISTENT);
    assertThat(LittlesLaw.check(7.6, 2, 3, 20, 0.25).status()).isEqualTo(Status.INCONSISTENT);
    assertThat(LittlesLaw.check(4.79, 2, 3, 20, 0.25).status()).isEqualTo(Status.INCONSISTENT);
  }

  @Test
  void tooFewCompletionsGiveNoVerdict() {
    LittlesLaw check = LittlesLaw.check(100, 2, 3, LittlesLaw.MIN_COMPLETIONS - 1, 0.25);
    assertThat(check.status()).isEqualTo(Status.INSUFFICIENT_DATA);
    assertThat(LittlesLaw.check(6, 2, 3, LittlesLaw.MIN_COMPLETIONS, 0.25).status())
        .isEqualTo(Status.CONSISTENT);
  }

  @Test
  void wipWithoutExpectedFlowIsInconsistent() {
    LittlesLaw check = LittlesLaw.check(3, 0, 0, 10, 0.25);
    assertThat(check.ratio()).isNull();
    assertThat(check.status()).isEqualTo(Status.INCONSISTENT);
    assertThat(LittlesLaw.check(0, 0, 0, 10, 0.25).status()).isEqualTo(Status.CONSISTENT);
  }

  @Test
  void rejectsInvalidInputs() {
    assertThatThrownBy(() -> LittlesLaw.check(1, 1, 1, 10, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LittlesLaw.check(-1, 1, 1, 10, 0.25))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LittlesLaw.check(1, Double.NaN, 1, 10, 0.25))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LittlesLaw.check(1, 1, Double.POSITIVE_INFINITY, 10, 0.25))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
