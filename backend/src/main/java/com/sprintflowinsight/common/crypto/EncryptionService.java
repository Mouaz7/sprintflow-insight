package com.sprintflowinsight.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * AES-256-GCM for secrets stored by the application.
 *
 * <p>A value is stored as the envelope {@code sf1:<keyId>:<base64url(iv | ciphertext | tag)>}
 * with a fresh random 96-bit IV and a 128-bit tag. The version, key id and a caller-chosen context
 * (for example {@code "webhook.secret"}) are authenticated as associated data, so changing any of
 * them, or moving a ciphertext to another field, fails decryption instead of yielding wrong data.
 *
 * <p>The bean is created eagerly at startup and refuses to start without a valid key.
 */
@Service
public class EncryptionService {
  public static final String VERSION = "sf1";
  static final int IV_BYTES = 12;
  static final int TAG_BITS = 128;
  private static final Pattern KEY_ID = Pattern.compile("[0-9a-f]{8}");
  private static final SecureRandom RANDOM = new SecureRandom();

  private final Keyring keyring;

  @Autowired
  public EncryptionService(
      @Value("${app.encryption.key:}") String key,
      @Value("${app.encryption.previous-keys:}") String previousKeys) {
    this(Keyring.parse(key, previousKeys));
  }

  public EncryptionService(Keyring keyring) {
    this.keyring = keyring;
  }

  public String currentKeyId() {
    return keyring.currentId();
  }

  /** Envelope prefix shared by every value encrypted with the current key. */
  public String currentPrefix() {
    return VERSION + ":" + keyring.currentId() + ":";
  }

  public String encrypt(String plaintext, String context) {
    if (plaintext == null) return null;
    byte[] iv = new byte[IV_BYTES];
    RANDOM.nextBytes(iv);
    String keyId = keyring.currentId();
    try {
      Cipher cipher = cipher(Cipher.ENCRYPT_MODE, keyring.currentKey(), iv, keyId, context);
      byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] payload = ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array();
      return VERSION
          + ":"
          + keyId
          + ":"
          + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
    } catch (GeneralSecurityException failure) {
      throw new EncryptionException("Encryption failed.", failure);
    }
  }

  public String decrypt(String envelope, String context) {
    if (envelope == null) return null;
    Parsed parsed = parse(envelope);
    SecretKey key =
        keyring
            .find(parsed.keyId())
            .orElseThrow(
                () ->
                    new EncryptionException(
                        "Encryption key " + parsed.keyId() + " is not configured."));
    try {
      Cipher cipher =
          cipher(Cipher.DECRYPT_MODE, key, parsed.iv(), parsed.keyId(), context);
      return new String(cipher.doFinal(parsed.sealed()), StandardCharsets.UTF_8);
    } catch (AEADBadTagException tampered) {
      throw new EncryptionException("Encrypted value failed authentication.", tampered);
    } catch (GeneralSecurityException failure) {
      throw new EncryptionException("Decryption failed.", failure);
    }
  }

  /** The key id an envelope was written with. */
  public String keyIdOf(String envelope) {
    return parse(envelope).keyId();
  }

  public boolean isCurrent(String envelope) {
    return envelope != null && envelope.startsWith(currentPrefix());
  }

  /** Decrypts with whichever key wrote the value and encrypts again with the current key. */
  public String reEncrypt(String envelope, String context) {
    return isCurrent(envelope) ? envelope : encrypt(decrypt(envelope, context), context);
  }

  private static Cipher cipher(int mode, SecretKey key, byte[] iv, String keyId, String context)
      throws GeneralSecurityException {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
    cipher.updateAAD(
        (VERSION + ":" + keyId + ":" + (context == null ? "" : context))
            .getBytes(StandardCharsets.UTF_8));
    return cipher;
  }

  private static Parsed parse(String envelope) {
    String[] parts = envelope.split(":", -1);
    if (parts.length != 3 || !VERSION.equals(parts[0]) || !KEY_ID.matcher(parts[1]).matches())
      throw new EncryptionException("Encrypted value has an unknown format.");
    byte[] payload;
    try {
      payload = Base64.getUrlDecoder().decode(parts[2]);
    } catch (IllegalArgumentException malformed) {
      throw new EncryptionException("Encrypted value has an unknown format.", malformed);
    }
    if (payload.length < IV_BYTES + TAG_BITS / 8)
      throw new EncryptionException("Encrypted value is truncated.");
    byte[] iv = java.util.Arrays.copyOfRange(payload, 0, IV_BYTES);
    byte[] sealed = java.util.Arrays.copyOfRange(payload, IV_BYTES, payload.length);
    return new Parsed(parts[1], iv, sealed);
  }

  private record Parsed(String keyId, byte[] iv, byte[] sealed) {}
}
