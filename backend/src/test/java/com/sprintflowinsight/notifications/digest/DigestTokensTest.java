package com.sprintflowinsight.notifications.digest;

import static org.assertj.core.api.Assertions.*;

import com.sprintflowinsight.common.crypto.EncryptionService;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class DigestTokensTest {
  @Test
  void authenticatesTheAccountEpochAndExpiryAndRejectsTampering() {
    var tokens =
        new DigestTokens(
            new EncryptionService(Base64.getEncoder().encodeToString(new byte[32]), ""));
    Instant now = Instant.parse("2026-11-19T08:00:00Z");
    var row = new DigestSubscription(5L);
    row.configure(DigestFrequency.DAILY, "UTC", now);
    String token = tokens.create(row, now);
    assertThat(tokens.verify(token, now))
        .isEqualTo(new DigestTokens.Claim(5L, row.getTokenVersion()));
    assertThatThrownBy(() -> tokens.verify(token, now.plusSeconds(90L * 86400)))
        .hasMessageContaining("invalid or has expired");
    for (String bad :
        new String[] {"bad", token.substring(0, token.length() - 8) + "AAAAAAAA", "x".repeat(2049)})
      assertThatThrownBy(() -> tokens.verify(bad, now))
          .hasMessageContaining("invalid or has expired");
    assertThatThrownBy(() -> tokens.verify(null, now))
        .hasMessageContaining("invalid or has expired");
  }
}
