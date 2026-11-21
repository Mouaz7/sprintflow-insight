package com.sprintflowinsight.auth;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 with the 30-second, six-digit HMAC-SHA-1 interoperable authenticator profile. */
final class TotpCodes {
  static final int SECRET_BYTES = 20;
  private static final long STEP_SECONDS = 30;
  private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

  private TotpCodes() {}

  static OptionalLong match(byte[] secret, String supplied, Instant now, long afterStep) {
    if (supplied == null || !supplied.matches("[0-9]{6}")) return OptionalLong.empty();
    byte[] candidate = supplied.getBytes(StandardCharsets.US_ASCII);
    long current = Math.floorDiv(now.getEpochSecond(), STEP_SECONDS);
    for (long step = current + 1; step >= Math.max(0, current - 1); step--) {
      if (step > afterStep
          && MessageDigest.isEqual(
              candidate, generate(secret, step, 6).getBytes(StandardCharsets.US_ASCII))) {
        return OptionalLong.of(step);
      }
    }
    return OptionalLong.empty();
  }

  static String generate(byte[] secret, long step, int digits) {
    if (digits != 6 && digits != 8) throw new IllegalArgumentException("Unsupported TOTP digits.");
    try {
      var mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(secret, "HmacSHA1"));
      byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
      int offset = hash[hash.length - 1] & 15;
      int number = ByteBuffer.wrap(hash).getInt(offset) & Integer.MAX_VALUE;
      int modulus = digits == 6 ? 1_000_000 : 100_000_000;
      return String.format(Locale.ROOT, "%0" + digits + "d", number % modulus);
    } catch (GeneralSecurityException error) {
      throw new IllegalStateException("HMAC-SHA-1 is unavailable.", error);
    }
  }

  static String base32(byte[] bytes) {
    var encoded = new StringBuilder();
    int buffer = 0;
    int bits = 0;
    for (byte value : bytes) {
      buffer = (buffer << 8) | (value & 255);
      bits += 8;
      while (bits >= 5) {
        bits -= 5;
        encoded.append(BASE32.charAt((buffer >>> bits) & 31));
      }
    }
    if (bits != 0) encoded.append(BASE32.charAt((buffer << (5 - bits)) & 31));
    return encoded.toString();
  }
}
