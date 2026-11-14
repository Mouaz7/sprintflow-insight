package com.sprintflowinsight.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class SecureTokensTest {
  @Test
  void tokensAreUrlSafe256BitValuesThatDoNotRepeat() {
    var seen = new HashSet<String>();
    for (int i = 0; i < 10_000; i++) {
      String token = SecureTokens.token();
      assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
      assertThat(seen.add(token)).isTrue();
    }
  }

  @Test
  void sha256MatchesTheStandardTestVector() {
    assertThat(SecureTokens.sha256("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }
}
