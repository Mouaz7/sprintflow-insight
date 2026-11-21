package com.sprintflowinsight.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.common.crypto.EncryptedColumn;
import com.sprintflowinsight.common.crypto.EncryptionService;
import com.sprintflowinsight.support.HttpFlowTest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

class MfaHttpIntegrationTest extends HttpFlowTest {
  private static final String PASSWORD = "secure-password";
  private static final String BASE = "/api/auth/two-factor";
  private static final EncryptedColumn SECRET =
      EncryptedColumn.of("account_mfa", "user_id", "secret_ciphertext");

  @Autowired EncryptionService encryption;

  @Test
  void requiresASecondFactorAndConsumesRecoveryCodesOnce() {
    registerTestUser();
    String email = testEmail;
    var enrollment = enroll();
    assertThat(enrollment.recovery()).hasSize(10).doesNotHaveDuplicates();
    assertThat(request(HttpMethod.GET, BASE, null).getBody())
        .containsEntry("enabled", true)
        .containsEntry("recoveryCodesRemaining", 10);

    firstFactor(email);
    assertThat(request(HttpMethod.GET, "/api/auth/me", null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    // Enrollment consumed the current code. The next step is accepted within the skew window.
    String current = code(enrollment.secret(), 1);
    assertThat(verify(current)).isEqualTo(HttpStatus.OK);
    assertThat(request(HttpMethod.GET, "/api/auth/me", null).getBody())
        .containsEntry("email", email);

    firstFactor(email);
    assertThat(verify(current)).isEqualTo(HttpStatus.UNAUTHORIZED);
    String recovery = enrollment.recovery().getFirst();
    assertThat(verify(recovery)).isEqualTo(HttpStatus.OK);
    assertThat(request(HttpMethod.GET, BASE, null).getBody())
        .containsEntry("recoveryCodesRemaining", 9);

    firstFactor(email);
    assertThat(verify(recovery)).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(verify(enrollment.recovery().get(1))).isEqualTo(HttpStatus.OK);
    assertThat(
            request(
                    HttpMethod.POST,
                    BASE + "/disable",
                    Map.of("password", PASSWORD, "code", enrollment.recovery().get(2)))
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(request(HttpMethod.GET, BASE, null).getBody()).containsEntry("enabled", false);
    sessionCookie = null;
    assertThat(login(email).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void rateLimitsEachChallengeAndTheAccountAcrossNewChallenges() {
    registerTestUser();
    String email = testEmail;
    var enrollment = enroll();
    for (int round = 0; round < 2; round++) {
      firstFactor(email);
      for (int attempt = 0; attempt < 5; attempt++)
        assertThat(verify("invalid code")).isEqualTo(HttpStatus.UNAUTHORIZED);
      assertThat(verify(enrollment.recovery().getFirst())).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
    firstFactor(email);
    assertThat(verify(enrollment.recovery().getFirst())).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    long user = userRepository.findByEmail(email).orElseThrow().getId();
    jdbc.update(
        "update account_mfa set last_failure=clock_timestamp()-interval '11 minutes' where"
            + " user_id=?",
        user);
    assertThat(verify(enrollment.recovery().getFirst())).isEqualTo(HttpStatus.OK);
  }

  @Test
  void organizationOwnerControlsWorkspaceRequirementsAndMembersCanEnroll() {
    registerTestUser();
    String owner = sessionCookie;
    String ownerEmail = testEmail;
    String slug = "mfa-" + UUID.randomUUID();
    String org = "/api/organizations/" + slug;
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/organizations",
                    Map.of("name", "Secure workspace", "slug", slug))
                .getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(
            request(HttpMethod.PUT, org + "/security", Map.of("requireTwoFactor", true))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);

    registerTestUser();
    String admin = sessionCookie;
    String adminEmail = testEmail;
    sessionCookie = owner;
    testEmail = ownerEmail;
    var invite =
        request(HttpMethod.POST, org + "/invites", Map.of("email", adminEmail, "role", "Admin"));
    sessionCookie = admin;
    assertThat(
            request(
                    HttpMethod.POST,
                    org + "/invites/accept",
                    Map.of("token", invite.getBody().get("token")))
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(
            request(HttpMethod.PUT, org + "/security", Map.of("requireTwoFactor", false))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    sessionCookie = owner;
    enroll();
    assertThat(
            request(HttpMethod.PUT, org + "/security", Map.of("requireTwoFactor", true))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var project =
        request(HttpMethod.POST, org + "/projects", Map.of("name", "Private org project"));
    assertThat(project.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    long projectId = id(project);
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/projects/" + projectId + "/members",
                    Map.of("email", adminEmail, "role", "Viewer"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    sessionCookie = admin;
    testEmail = adminEmail;
    assertThat(request(HttpMethod.GET, "/api/auth/me", null).getBody())
        .containsEntry("mfaEnrollmentRequired", true);
    assertThat(request(HttpMethod.GET, org + "/members", null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(request(HttpMethod.GET, "/api/projects/" + projectId, null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(request(HttpMethod.GET, BASE, null).getBody())
        .containsEntry("requiredByOrganization", true);
    var memberEnrollment = enroll();
    assertThat(request(HttpMethod.GET, "/api/projects/" + projectId, null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            request(
                    HttpMethod.POST,
                    BASE + "/disable",
                    Map.of("password", PASSWORD, "code", memberEnrollment.recovery().getFirst()))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  private record Enrollment(byte[] secret, List<String> recovery) {}

  private Enrollment enroll() {
    var setup = request(HttpMethod.POST, BASE + "/setup", Map.of("password", PASSWORD));
    assertThat(setup.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(setup.getHeaders().getCacheControl()).contains("no-store");
    long user = userRepository.findByEmail(testEmail).orElseThrow().getId();
    String ciphertext =
        jdbc.queryForObject(
            "select secret_ciphertext from account_mfa where user_id=?", String.class, user);
    byte[] raw = Base64.getDecoder().decode(encryption.decrypt(ciphertext, SECRET.context()));
    assertThat(ciphertext).doesNotContain((String) setup.getBody().get("secret"));
    assertThat(TotpCodes.base32(raw)).isEqualTo(setup.getBody().get("secret"));
    var enabled =
        request(
            HttpMethod.POST, BASE + "/enable", Map.of("password", PASSWORD, "code", code(raw, 0)));
    assertThat(enabled.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<String> recovery = (List<String>) enabled.getBody().get("recoveryCodes");
    return new Enrollment(raw, recovery);
  }

  private void firstFactor(String email) {
    sessionCookie = null;
    var response = login(email);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody())
        .containsEntry("secondFactorRequired", true)
        .doesNotContainKey("id");
  }

  private org.springframework.http.ResponseEntity<Map> login(String email) {
    return request(
        HttpMethod.POST, "/api/auth/login", Map.of("email", email, "password", PASSWORD));
  }

  private HttpStatus verify(String code) {
    return HttpStatus.valueOf(
        request(HttpMethod.POST, BASE + "/verify", Map.of("code", code)).getStatusCode().value());
  }

  private static String code(byte[] raw, long offset) {
    return TotpCodes.generate(raw, (Instant.now().getEpochSecond() / 30) + offset, 6);
  }
}
