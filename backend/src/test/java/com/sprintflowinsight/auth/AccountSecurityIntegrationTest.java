package com.sprintflowinsight.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Account security over real HTTP and real (JDBC-backed) sessions: credential changes sign the
 * account out elsewhere, recovery does not leak account state, and Google sign-in trusts only the
 * verifier's verdict.
 */
@org.springframework.context.annotation.Import(
    com.sprintflowinsight.support.PostgresTestContainer.class)
@org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.security.auth-rate-limit.enabled=false",
      "app.google.client-id=test-client.apps.googleusercontent.com"
    })
class AccountSecurityIntegrationTest {
  private static final String PASSWORD = "first-password";

  @Autowired private TestRestTemplate rest;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder encoder;
  @MockitoBean private GoogleTokenVerifier google;

  @Test
  void changingThePasswordSignsOutOtherSessionsAndRotatesTheCurrentOne() {
    String email = newEmail();
    Browser laptop = new Browser();
    laptop.post(
        "/api/auth/register", Map.of("email", email, "password", PASSWORD, "displayName", "Ada"));
    Browser phone = new Browser();
    assertThat(
            phone
                .post("/api/auth/login", Map.of("email", email, "password", PASSWORD))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(phone.get("/api/auth/me").getStatusCode()).isEqualTo(HttpStatus.OK);

    String before = laptop.cookie;
    var changed =
        laptop.post(
            "/api/auth/password/change",
            Map.of("email", email, "currentPassword", PASSWORD, "newPassword", "second-password"));
    assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(laptop.cookie).as("session id is rotated").isNotEqualTo(before);

    assertThat(laptop.get("/api/auth/me").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(phone.get("/api/auth/me").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void recoveryCodeResetRevealsNothingAndSignsOutEverywhere() {
    String withCode = newEmail();
    String withoutCode = newEmail();
    new Browser()
        .post(
            "/api/auth/register",
            Map.of("email", withoutCode, "password", PASSWORD, "displayName", "B"));
    Browser signedIn = new Browser();
    signedIn.post(
        "/api/auth/register", Map.of("email", withCode, "password", PASSWORD, "displayName", "A"));
    User user = users.findByEmail(withCode).orElseThrow();
    user.setRecoveryCodeHash(encoder.encode("correct-code"));
    users.save(user);

    var unknown = recover(newEmail(), "correct-code");
    var noCode = recover(withoutCode, "correct-code");
    var wrongCode = recover(withCode, "wrong-code");
    for (var response : java.util.List.of(unknown, noCode, wrongCode)) {
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
      assertThat(response.getBody())
          .containsEntry("detail", "Email or recovery code is incorrect.");
    }

    assertThat(recover(withCode, "correct-code").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(signedIn.get("/api/auth/me").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void googleSignInCreatesTheAccountOnlyForAVerifiedToken() {
    String email = newEmail();
    when(google.verify(anyString(), anyString())).thenReturn(Optional.empty());
    when(google.verify(eq("valid-token"), eq("test-client.apps.googleusercontent.com")))
        .thenReturn(Optional.of(new GoogleTokenVerifier.VerifiedGoogleUser(email, "Grace", null)));

    Browser browser = new Browser();
    assertThat(browser.post("/api/auth/google", Map.of("credential", "forged")).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(users.findByEmail(email)).isEmpty();

    var signedIn = browser.post("/api/auth/google", Map.of("credential", "valid-token"));
    assertThat(signedIn.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(users.findByEmail(email)).get().extracting(User::getDisplayName).isEqualTo("Grace");
    assertThat(browser.get("/api/auth/me").getBody()).containsEntry("email", email);
  }

  private ResponseEntity<Map> recover(String email, String code) {
    return new Browser()
        .post(
            "/api/auth/password/reset/recovery",
            Map.of("email", email, "recoveryCode", code, "newPassword", "after-recovery"));
  }

  private static String newEmail() {
    return "account-" + UUID.randomUUID() + "@example.test";
  }

  /** A cookie jar: one browser profile with its own session. */
  private class Browser {
    String cookie;

    ResponseEntity<Map> get(String path) {
      return exchange(path, HttpMethod.GET, null, headers());
    }

    ResponseEntity<Map> post(String path, Object body) {
      ResponseEntity<Map> csrf = get("/api/auth/csrf");
      HttpHeaders headers = headers();
      headers.set((String) csrf.getBody().get("headerName"), (String) csrf.getBody().get("token"));
      return exchange(path, HttpMethod.POST, body, headers);
    }

    private ResponseEntity<Map> exchange(
        String path, HttpMethod method, Object body, HttpHeaders headers) {
      ResponseEntity<Map> response =
          rest.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
      String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
      if (setCookie != null && setCookie.startsWith("SPRINTFLOW_SESSION="))
        cookie = setCookie.split(";", 2)[0];
      return response;
    }

    private HttpHeaders headers() {
      HttpHeaders headers = new HttpHeaders();
      if (cookie != null) headers.set(HttpHeaders.COOKIE, cookie);
      return headers;
    }
  }
}
