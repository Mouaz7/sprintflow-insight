package com.sprintflowinsight.notifications.digest;

import com.sprintflowinsight.common.crypto.EncryptionService;
import com.sprintflowinsight.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Opaque AES-GCM authenticated capabilities; the existing keyring supports key rotation. */
@Component
public class DigestTokens {
  private static final String CONTEXT = "digest.unsubscribe.v1";
  private final EncryptionService crypto;

  public DigestTokens(EncryptionService crypto) {
    this.crypto = crypto;
  }

  public String create(DigestSubscription row, Instant now) {
    String payload =
        row.getUserId()
            + "|"
            + row.getTokenVersion()
            + "|"
            + now.plusSeconds(90L * 86400).getEpochSecond();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(crypto.encrypt(payload, CONTEXT).getBytes(StandardCharsets.UTF_8));
  }

  public record Claim(Long userId, UUID version) {}

  public Claim verify(String token, Instant now) {
    try {
      if (token == null || token.length() > 2048) throw new IllegalArgumentException();
      String[] parts =
          crypto
              .decrypt(
                  new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8), CONTEXT)
              .split("\\|", -1);
      if (parts.length != 3 || Long.parseLong(parts[2]) <= now.getEpochSecond())
        throw new IllegalArgumentException();
      long id = Long.parseLong(parts[0]);
      if (id < 1) throw new IllegalArgumentException();
      return new Claim(id, UUID.fromString(parts[1]));
    } catch (RuntimeException invalid) {
      throw ApiException.badRequest("This unsubscribe link is invalid or has expired.");
    }
  }
}
