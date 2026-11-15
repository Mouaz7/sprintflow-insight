package com.sprintflowinsight.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AccountService accounts;
  private final PasswordResetService passwords;
  private final GoogleLoginService google;
  private final SessionUser sessionUser;

  public AuthController(
      AccountService accounts,
      PasswordResetService passwords,
      GoogleLoginService google,
      SessionUser sessionUser) {
    this.accounts = accounts;
    this.passwords = passwords;
    this.google = google;
    this.sessionUser = sessionUser;
  }

  @GetMapping("/integrations")
  public Map<String, Boolean> integrations(HttpSession session) {
    sessionUser.email(session);
    return Map.of(
        "gmailReady",
        passwords.emailReady(),
        "googleReady",
        !google.activeGoogleClientId().isBlank());
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @GetMapping("/me")
  public User me(HttpSession session) {
    return accounts.me(session);
  }

  @PostMapping("/register")
  public ResponseEntity<?> register(
      @Valid @RequestBody Credentials credentials,
      HttpServletRequest request,
      HttpServletResponse response) {
    return accounts.register(credentials, request, response);
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(
      @Valid @RequestBody Credentials credentials,
      HttpServletRequest request,
      HttpServletResponse response) {
    return accounts.login(credentials, request, response);
  }

  @PostMapping("/password/change")
  public ResponseEntity<?> changePassword(
      @Valid @RequestBody PasswordChangeRequest request,
      HttpSession session,
      HttpServletRequest servletRequest) {
    return accounts.changePassword(request, session, servletRequest);
  }

  @PutMapping("/profile")
  public ResponseEntity<?> updateProfile(
      @Valid @RequestBody ProfileUpdateRequest request,
      HttpSession session,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    return accounts.updateProfile(request, session, servletRequest, servletResponse);
  }

  @DeleteMapping("/account")
  public ResponseEntity<?> deleteAccount(
      @Valid @RequestBody DeleteAccountRequest request, HttpSession session) {
    return accounts.deleteAccount(request, session);
  }

  @PostMapping("/password/reset/request")
  public ResponseEntity<?> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
    return passwords.requestPasswordReset(request);
  }

  @GetMapping("/password/reset/available")
  public ResponseEntity<?> passwordRecoveryAvailable() {
    return passwords.passwordRecoveryAvailable();
  }

  @GetMapping("/google/config")
  public ResponseEntity<?> googleLoginConfiguration() {
    return google.googleLoginConfiguration();
  }

  @PostMapping("/google")
  public ResponseEntity<?> loginWithGoogle(
      @Valid @RequestBody GoogleLoginRequest request,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    return google.loginWithGoogle(request, servletRequest, servletResponse);
  }

  /**
   * Unknown account, account without a recovery code and wrong code all produce the same 401 and
   * cost one BCrypt comparison, so the endpoint does not reveal which accounts exist or have a
   * code.
   */
  @PostMapping("/password/reset/recovery")
  public ResponseEntity<?> resetPasswordWithRecoveryCode(
      @Valid @RequestBody RecoveryPasswordResetRequest request) {
    return passwords.resetPasswordWithRecoveryCode(request);
  }

  @PostMapping("/password/reset/confirm")
  public ResponseEntity<?> confirmPasswordReset(
      @Valid @RequestBody PasswordResetConfirmRequest request) {
    return passwords.confirmPasswordReset(request);
  }
}
