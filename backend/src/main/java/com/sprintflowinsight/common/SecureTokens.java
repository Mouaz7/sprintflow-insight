package com.sprintflowinsight.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Bearer secrets (password-reset links, shared-report links): 256 random bits, URL-safe, and
 * stored only as a SHA-256 digest so a database leak does not reveal working links.
 */
public final class SecureTokens {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;

  private SecureTokens() {}

  /** A fresh URL-safe token with 256 bits of entropy (43 characters). */
  public static String token() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** Lower-case hex SHA-256 of the UTF-8 value; this is what gets persisted. */
  public static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is required by every Java platform.", impossible);
    }
  }
}
