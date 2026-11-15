package com.sprintflowinsight.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * The current data-encryption key plus retired keys that may still decrypt old values. Key ids are
 * derived from the key itself (a truncated SHA-256 fingerprint), so operators never assign or
 * mistype them and a ciphertext always names the exact key that produced it.
 */
public final class Keyring {
  public static final String KEY_ENVIRONMENT = "SPRINTFLOW_ENCRYPTION_KEY";
  public static final int KEY_BYTES = 32;
  private static final String FINGERPRINT_LABEL = "sprintflow-encryption-key-id:";

  private final Entry current;
  private final Map<String, SecretKey> keys;

  private Keyring(Entry current, Map<String, SecretKey> keys) {
    this.current = current;
    this.keys = Collections.unmodifiableMap(keys);
  }

  /**
   * @param currentBase64 the active key, standard Base64 of exactly 32 random bytes
   * @param previousBase64 retired keys, comma separated; may be blank
   * @throws IllegalStateException with an operator-facing message; never echoes key material
   */
  public static Keyring parse(String currentBase64, String previousBase64) {
    if (currentBase64 == null || currentBase64.isBlank())
      throw new IllegalStateException(
          KEY_ENVIRONMENT
              + " is not set. Generate one with: head -c 32 /dev/urandom | base64 (the start"
              + " scripts write it to .env automatically).");
    Entry current = entry(currentBase64, KEY_ENVIRONMENT);
    Map<String, SecretKey> keys = new LinkedHashMap<>();
    keys.put(current.id(), current.key());
    if (previousBase64 != null) {
      for (String value : previousBase64.split(",")) {
        if (value.isBlank()) continue;
        Entry previous = entry(value, "SPRINTFLOW_ENCRYPTION_PREVIOUS_KEYS");
        if (keys.putIfAbsent(previous.id(), previous.key()) != null)
          throw new IllegalStateException(
              "SPRINTFLOW_ENCRYPTION_PREVIOUS_KEYS repeats key " + previous.id() + ".");
      }
    }
    return new Keyring(current, keys);
  }

  public static Keyring of(byte[] current, byte[]... previous) {
    Base64.Encoder encoder = Base64.getEncoder();
    List<String> retired = new ArrayList<>();
    for (byte[] key : previous) retired.add(encoder.encodeToString(key));
    return parse(encoder.encodeToString(current), String.join(",", retired));
  }

  /** Eight lower-case hex characters identifying {@code key}. */
  public static String keyId(byte[] key) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(FINGERPRINT_LABEL.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest.digest(key), 0, 4);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is required by every Java platform.", impossible);
    }
  }

  public String currentId() {
    return current.id();
  }

  SecretKey currentKey() {
    return current.key();
  }

  Optional<SecretKey> find(String keyId) {
    return Optional.ofNullable(keys.get(keyId));
  }

  /** Every key id that can still decrypt, the current one first. */
  public List<String> ids() {
    return List.copyOf(keys.keySet());
  }

  private static Entry entry(String base64, String source) {
    byte[] raw;
    try {
      raw = Base64.getDecoder().decode(base64.trim());
    } catch (IllegalArgumentException invalid) {
      throw new IllegalStateException(source + " must be standard Base64.");
    }
    if (raw.length != KEY_BYTES)
      throw new IllegalStateException(
          source + " must decode to exactly " + KEY_BYTES + " bytes (AES-256).");
    return new Entry(keyId(raw), new SecretKeySpec(raw, "AES"));
  }

  private record Entry(String id, SecretKey key) {}
}
