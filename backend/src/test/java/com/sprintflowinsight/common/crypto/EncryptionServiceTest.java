package com.sprintflowinsight.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class EncryptionServiceTest {
  private static final SecureRandom RANDOM = new SecureRandom();

  static byte[] key() {
    byte[] key = new byte[Keyring.KEY_BYTES];
    RANDOM.nextBytes(key);
    return key;
  }

  private final byte[] current = key();
  private final EncryptionService service = new EncryptionService(Keyring.of(current));

  @Test
  void roundTripsTextIncludingEmptyAndUnicode() {
    for (String plaintext : new String[] {"", "s3cret", "åäö ✓ 🔑", "x".repeat(10_000)}) {
      String envelope = service.encrypt(plaintext, "webhook.secret");
      assertThat(service.decrypt(envelope, "webhook.secret")).isEqualTo(plaintext);
    }
  }

  @Test
  void nullStaysNull() {
    assertThat(service.encrypt(null, "c")).isNull();
    assertThat(service.decrypt(null, "c")).isNull();
    assertThat(service.isCurrent(null)).isFalse();
  }

  @Test
  void envelopeNamesVersionAndKeyAndNeverContainsThePlaintext() {
    String envelope = service.encrypt("visible-secret", "c");
    assertThat(envelope).startsWith("sf1:" + Keyring.keyId(current) + ":");
    assertThat(envelope).startsWith(service.currentPrefix());
    assertThat(envelope).doesNotContain("visible-secret");
    assertThat(service.keyIdOf(envelope)).isEqualTo(service.currentKeyId());
    byte[] payload = Base64.getUrlDecoder().decode(envelope.split(":")[2]);
    assertThat(payload).hasSize(EncryptionService.IV_BYTES + "visible-secret".length() + 16);
  }

  @Test
  void everyEncryptionUsesAFreshIv() {
    String first = service.encrypt("same", "c");
    String second = service.encrypt("same", "c");
    assertThat(first).isNotEqualTo(second);
    assertThat(first.split(":")[2].substring(0, 16)).isNotEqualTo(second.split(":")[2].substring(0, 16));
  }

  @Test
  void detectsATamperedCiphertext() {
    String envelope = service.encrypt("payload", "c");
    String[] parts = envelope.split(":");
    byte[] payload = Base64.getUrlDecoder().decode(parts[2]);
    payload[payload.length - 1] ^= 1;
    String tampered =
        parts[0] + ":" + parts[1] + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
    assertThatThrownBy(() -> service.decrypt(tampered, "c"))
        .isInstanceOf(EncryptionException.class)
        .hasMessage("Encrypted value failed authentication.");
  }

  @Test
  void bindsTheContextSoValuesCannotMoveBetweenFields() {
    String envelope = service.encrypt("payload", "webhook.secret");
    assertThatThrownBy(() -> service.decrypt(envelope, "integration.token"))
        .isInstanceOf(EncryptionException.class);
    assertThatThrownBy(() -> service.decrypt(envelope, null))
        .isInstanceOf(EncryptionException.class);
    String withoutContext = service.encrypt("payload", null);
    assertThat(service.decrypt(withoutContext, null)).isEqualTo("payload");
  }

  @Test
  void authenticatesTheKeyId() {
    byte[] other = key();
    EncryptionService both = new EncryptionService(Keyring.of(current, other));
    String envelope = both.encrypt("payload", "c");
    String relabelled = envelope.replace(":" + Keyring.keyId(current) + ":", ":" + Keyring.keyId(other) + ":");
    assertThatThrownBy(() -> both.decrypt(relabelled, "c")).isInstanceOf(EncryptionException.class);
  }

  @Test
  void rejectsUnknownKeysAndMalformedEnvelopes() {
    EncryptionService stranger = new EncryptionService(Keyring.of(key()));
    String foreign = stranger.encrypt("payload", "c");
    assertThatThrownBy(() -> service.decrypt(foreign, "c"))
        .isInstanceOf(EncryptionException.class)
        .hasMessageContaining("is not configured");
    for (String malformed :
        new String[] {
          "", "plain", "sf1:abc", "sf2:0123abcd:AAAA", "sf1:XYZ12345:AAAA", "sf1:0123abcd:***",
          "sf1:0123abcd:AAAA", "sf1:0123abcd:AAAA:extra", "sf1:0123abc:AAAA"
        })
      assertThatThrownBy(() -> service.decrypt(malformed, "c"))
          .as(malformed)
          .isInstanceOf(EncryptionException.class);
  }

  @Test
  void reEncryptMovesOldValuesToTheCurrentKeyAndLeavesCurrentOnesAlone() {
    byte[] next = key();
    String old = service.encrypt("rotate me", "c");
    EncryptionService rotated = new EncryptionService(Keyring.of(next, current));
    assertThat(rotated.isCurrent(old)).isFalse();
    assertThat(rotated.decrypt(old, "c")).isEqualTo("rotate me");

    String fresh = rotated.reEncrypt(old, "c");
    assertThat(rotated.isCurrent(fresh)).isTrue();
    assertThat(rotated.keyIdOf(fresh)).isEqualTo(Keyring.keyId(next));
    assertThat(rotated.decrypt(fresh, "c")).isEqualTo("rotate me");
    assertThat(rotated.reEncrypt(fresh, "c")).isSameAs(fresh);

    EncryptionService retiredRemoved = new EncryptionService(Keyring.of(next));
    assertThat(retiredRemoved.decrypt(fresh, "c")).isEqualTo("rotate me");
    assertThatThrownBy(() -> retiredRemoved.decrypt(old, "c")).isInstanceOf(EncryptionException.class);
  }

  @Test
  void twoStepRotationKeepsMixedVersionsReadableDuringRollingDeploys() {
    byte[] next = key();
    // Step 0: the new key is readable everywhere before any instance writes with it.
    EncryptionService staged = new EncryptionService(Keyring.of(current, next));
    assertThat(staged.currentKeyId()).isEqualTo(Keyring.keyId(current));
    // Step 1, mid-rollout: some instances already write with the new key, others do not.
    EncryptionService swapped = new EncryptionService(Keyring.of(next, current));
    String fromSwapped = swapped.encrypt("new write", "c");
    String fromStaged = staged.encrypt("old write", "c");
    assertThat(staged.decrypt(fromSwapped, "c")).isEqualTo("new write");
    assertThat(swapped.decrypt(fromStaged, "c")).isEqualTo("old write");
    // A lagging instance may re-encrypt back to the old key; the value stays readable.
    assertThat(swapped.decrypt(staged.reEncrypt(fromSwapped, "c"), "c")).isEqualTo("new write");
    // Skipping step 0 is what breaks: an instance without the new key cannot read new writes.
    assertThatThrownBy(() -> service.decrypt(fromSwapped, "c"))
        .isInstanceOf(EncryptionException.class);
  }

  @Test
  void springConstructorReadsBase64Configuration() {
    byte[] previous = key();
    EncryptionService configured =
        new EncryptionService(
            Base64.getEncoder().encodeToString(current),
            " " + Base64.getEncoder().encodeToString(previous) + " ,");
    assertThat(configured.currentKeyId()).isEqualTo(Keyring.keyId(current));
    String fromPrevious = new EncryptionService(Keyring.of(previous)).encrypt("x", "c");
    assertThat(configured.decrypt(fromPrevious, "c")).isEqualTo("x");
  }
}
