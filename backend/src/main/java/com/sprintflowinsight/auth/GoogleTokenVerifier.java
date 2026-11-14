package com.sprintflowinsight.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Verifies Google ID tokens. One HTTP transport and one verifier per client id are reused, so
 * Google's signing keys are cached between sign-ins instead of being fetched on every request.
 * A separate bean so tests can replace it without network access.
 */
@Component
public class GoogleTokenVerifier {
  /** The verified claims SprintFlow uses. */
  public record VerifiedGoogleUser(String email, String name, String picture) {}

  private final NetHttpTransport transport = new NetHttpTransport();
  private final Map<String, GoogleIdTokenVerifier> verifiers = new ConcurrentHashMap<>();

  /** Empty when the token is invalid, expired, for another audience or the email is unverified. */
  public Optional<VerifiedGoogleUser> verify(String credential, String clientId) {
    try {
      GoogleIdToken token =
          verifiers
              .computeIfAbsent(
                  clientId,
                  id ->
                      new GoogleIdTokenVerifier.Builder(transport, GsonFactory.getDefaultInstance())
                          .setAudience(List.of(id))
                          .build())
              .verify(credential);
      if (token == null || !Boolean.TRUE.equals(token.getPayload().getEmailVerified()))
        return Optional.empty();
      GoogleIdToken.Payload payload = token.getPayload();
      return Optional.of(
          new VerifiedGoogleUser(
              payload.getEmail(), (String) payload.get("name"), (String) payload.get("picture")));
    } catch (Exception invalid) {
      return Optional.empty();
    }
  }
}
