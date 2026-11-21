package com.sprintflowinsight.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TotpCodesTest {
  private static final byte[] SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

  @ParameterizedTest
  @CsvSource({
    "59,94287082",
    "1111111109,07081804",
    "1111111111,14050471",
    "1234567890,89005924",
    "2000000000,69279037",
    "20000000000,65353130"
  })
  void passesRfc6238Sha1Vectors(long seconds, String code) {
    assertThat(TotpCodes.generate(SECRET, seconds / 30, 8)).isEqualTo(code);
  }

  @Test
  void permitsOneStepOfSkewAndRejectsReusingAnAcceptedStep() {
    var time = Instant.ofEpochSecond(1234567890);
    long step = time.getEpochSecond() / 30;
    String previous = TotpCodes.generate(SECRET, step - 1, 6);
    String current = TotpCodes.generate(SECRET, step, 6);
    assertThat(TotpCodes.match(SECRET, previous, time, -1)).hasValue(step - 1);
    assertThat(TotpCodes.match(SECRET, current, time, step - 1)).hasValue(step);
    assertThat(TotpCodes.match(SECRET, current, time, step)).isEmpty();
    assertThat(TotpCodes.match(SECRET, TotpCodes.generate(SECRET, step - 2, 6), time, -1))
        .isEmpty();
    assertThat(TotpCodes.match(SECRET, "1234x6", time, -1)).isEmpty();
  }

  @Test
  void encodesAuthenticatorSecretsWithoutPadding() {
    assertThat(TotpCodes.base32("foobar".getBytes(StandardCharsets.US_ASCII)))
        .isEqualTo("MZXW6YTBOI");
    assertThat(TotpCodes.base32(SECRET)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
  }
}
