package com.sprintflowinsight.automation;

import static org.assertj.core.api.Assertions.assertThat;

import com.code_intelligence.jazzer.junit.FuzzTest;
import java.nio.charset.StandardCharsets;

class AutomationDslFuzzTest {
  @FuzzTest(maxDuration = "30s")
  void parsesOnlyBoundedRules(byte[] bytes) {
    try {
      var rule = AutomationDsl.parse(new String(bytes, StandardCharsets.UTF_8));
      assertThat(rule.trigger().kind()).isNotNull();
      assertThat(rule.conditions()).hasSizeLessThanOrEqualTo(AutomationDsl.MAX_CONDITIONS);
      assertThat(rule.action().value()).isNotBlank();
      assertThat(rule.action().value().length()).isLessThanOrEqualTo(1000);
    } catch (AutomationDsl.ParseException expected) {
      assertThat(expected.line()).isPositive();
    }
  }
}
