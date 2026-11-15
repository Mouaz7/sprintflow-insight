package com.sprintflowinsight.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class KeyringTest {
  private static String base64(byte[] key) {
    return Base64.getEncoder().encodeToString(key);
  }

  @Test
  void missingKeyFailsFastWithAnOperatorHint() {
    for (String missing : new String[] {null, "", "   "})
      assertThatThrownBy(() -> Keyring.parse(missing, ""))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("SPRINTFLOW_ENCRYPTION_KEY is not set")
          .hasMessageContaining("base64");
  }

  @Test
  void rejectsInvalidBase64AndWrongLengthsWithoutEchoingTheValue() {
    assertThatThrownBy(() -> Keyring.parse("not*base64!", null))
        .hasMessage("SPRINTFLOW_ENCRYPTION_KEY must be standard Base64.");
    String shortKey = base64(new byte[16]);
    assertThatThrownBy(() -> Keyring.parse(shortKey, null))
        .hasMessageContaining("exactly 32 bytes")
        .hasMessageNotContaining(shortKey);
    String good = base64(EncryptionServiceTest.key());
    assertThatThrownBy(() -> Keyring.parse(good, base64(new byte[31])))
        .hasMessageContaining("SPRINTFLOW_ENCRYPTION_PREVIOUS_KEYS");
  }

  @Test
  void rejectsARepeatedKey() {
    byte[] key = EncryptionServiceTest.key();
    assertThatThrownBy(() -> Keyring.of(key, key)).hasMessageContaining("repeats key");
    byte[] other = EncryptionServiceTest.key();
    assertThatThrownBy(() -> Keyring.of(key, other, other)).hasMessageContaining("repeats key");
  }

  @Test
  void idsAreStableFingerprintsWithTheCurrentKeyFirst() {
    byte[] current = EncryptionServiceTest.key();
    byte[] old = EncryptionServiceTest.key();
    Keyring keyring = Keyring.of(current, old);
    assertThat(Keyring.keyId(current)).matches("[0-9a-f]{8}").isEqualTo(Keyring.keyId(current.clone()));
    assertThat(Keyring.keyId(current)).isNotEqualTo(Keyring.keyId(old));
    assertThat(keyring.currentId()).isEqualTo(Keyring.keyId(current));
    assertThat(keyring.ids()).containsExactly(Keyring.keyId(current), Keyring.keyId(old));
    assertThat(keyring.find(Keyring.keyId(old))).isPresent();
    assertThat(keyring.find("00000000")).isEmpty();
    assertThat(Keyring.parse(base64(current), null).ids()).containsExactly(Keyring.keyId(current));
  }
}
