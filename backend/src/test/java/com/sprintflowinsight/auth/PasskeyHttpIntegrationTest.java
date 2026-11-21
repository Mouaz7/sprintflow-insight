package com.sprintflowinsight.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.common.SecureTokens;
import com.sprintflowinsight.support.HttpFlowTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.password-reset.base-url=http://localhost:8081")
class PasskeyHttpIntegrationTest extends HttpFlowTest {
  private static final String BASE = "/api/auth/passkeys";
  private static final Map<String, Object> ASSERTION = Map.of("credential", Map.of());

  @Test
  void onlyTheEnrollmentAccountCanAccessItsPasskeyManagement() {
    assertThat(request(HttpMethod.GET, BASE + "/config", null).getBody())
        .containsEntry("available", true)
        .containsEntry("origin", "http://localhost:8081");
    assertThat(request(HttpMethod.POST, BASE + "/registration/options", null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);

    registerTestUser();
    String ownerSession = sessionCookie;
    var options = request(HttpMethod.POST, BASE + "/registration/options", null);
    assertThat(options.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(options.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(options.getBody()).containsKeys("challenge", "authenticatorSelection");

    registerTestUser();
    var attestation = Map.of("label", "Security key", "credential", Map.of());
    var unrelated = request(HttpMethod.POST, BASE + "/registration/complete", attestation);
    assertThat(unrelated.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(unrelated.getBody()).containsEntry("detail", "Start a new passkey request.");
    assertThat(request(HttpMethod.DELETE, BASE + "/unowned", null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);

    sessionCookie = ownerSession;
    var owner = request(HttpMethod.POST, BASE + "/registration/complete", attestation);
    assertThat(owner.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(owner.getBody()).containsEntry("detail", "The passkey could not be registered.");
  }

  @Test
  void aLoginChallengeExpiresAndIsConsumedEvenAfterFailedVerification() {
    var first = request(HttpMethod.POST, BASE + "/login/options", null);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
    String challenge = (String) first.getBody().get("challenge");
    assertThat(challenge).isNotBlank();
    assertThat(first.getBody()).containsEntry("userVerification", "required");
    assertThat(request(HttpMethod.POST, BASE + "/login/complete", ASSERTION).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    var replay = request(HttpMethod.POST, BASE + "/login/complete", ASSERTION);
    assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(replay.getBody()).containsEntry("detail", "Start a new passkey request.");

    var second = request(HttpMethod.POST, BASE + "/login/options", null);
    String next = (String) second.getBody().get("challenge");
    assertThat(next).isNotBlank().isNotEqualTo(challenge);
    assertThat(
            jdbc.update(
                "update passkey_challenge set expires_at=clock_timestamp() where"
                    + " challenge_digest=?",
                SecureTokens.sha256(next)))
        .isEqualTo(1);
    assertThat(request(HttpMethod.POST, BASE + "/login/complete", ASSERTION).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(request(HttpMethod.GET, "/api/auth/me", null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
