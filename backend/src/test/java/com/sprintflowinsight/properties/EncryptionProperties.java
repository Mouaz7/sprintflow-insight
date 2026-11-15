package com.sprintflowinsight.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprintflowinsight.common.crypto.EncryptionException;
import com.sprintflowinsight.common.crypto.EncryptionService;
import com.sprintflowinsight.common.crypto.Keyring;
import java.util.Base64;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/** AES-GCM envelopes: every value round-trips, and every single-bit change is detected. */
class EncryptionProperties {
  private static final byte[] KEY = new byte[32];
  private static final byte[] NEXT = new byte[32];

  static {
    for (int i = 0; i < 32; i++) {
      KEY[i] = (byte) (i * 7 + 1);
      NEXT[i] = (byte) (255 - i);
    }
  }

  private final EncryptionService service = new EncryptionService(Keyring.of(KEY));

  @Property(tries = 300)
  void decryptInvertsEncrypt(@ForAll String plaintext, @ForAll String context) {
    assertThat(service.decrypt(service.encrypt(plaintext, context), context)).isEqualTo(plaintext);
  }

  @Property(tries = 300)
  void anySingleBitFlipInThePayloadIsRejected(
      @ForAll @Size(max = 200) String plaintext,
      @ForAll @IntRange(min = 0, max = 10_000) int position) {
    String envelope = service.encrypt(plaintext, "ctx");
    String[] parts = envelope.split(":");
    byte[] payload = Base64.getUrlDecoder().decode(parts[2]);
    int bit = position % (payload.length * 8);
    payload[bit / 8] ^= (byte) (1 << (bit % 8));
    String tampered =
        parts[0]
            + ":"
            + parts[1]
            + ":"
            + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
    assertThatThrownBy(() -> service.decrypt(tampered, "ctx"))
        .isInstanceOf(EncryptionException.class);
  }

  @Property(tries = 200)
  void aDifferentContextIsRejected(
      @ForAll @Size(max = 100) String plaintext, @ForAll String context, @ForAll String other) {
    Assume.that(!context.equals(other));
    String envelope = service.encrypt(plaintext, context);
    assertThatThrownBy(() -> service.decrypt(envelope, other))
        .isInstanceOf(EncryptionException.class);
  }

  @Property(tries = 200)
  void rotationPreservesEveryValue(@ForAll String plaintext) {
    String old = service.encrypt(plaintext, "ctx");
    EncryptionService rotated = new EncryptionService(Keyring.of(NEXT, KEY));
    String fresh = rotated.reEncrypt(old, "ctx");
    assertThat(rotated.isCurrent(fresh)).isTrue();
    assertThat(new EncryptionService(Keyring.of(NEXT)).decrypt(fresh, "ctx")).isEqualTo(plaintext);
  }

  @Property(tries = 300)
  void arbitraryInputNeverDecryptsOrThrowsAnythingElse(@ForAll String garbage) {
    assertThatThrownBy(() -> service.decrypt(garbage, "ctx"))
        .isInstanceOf(EncryptionException.class);
  }
}
