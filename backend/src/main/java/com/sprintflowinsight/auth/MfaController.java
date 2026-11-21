package com.sprintflowinsight.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/two-factor")
class MfaController {
  record Password(@NotBlank @Size(max = 72) String password) {
    @Override
    public String toString() {
      return "MfaPassword[redacted]";
    }
  }

  record PasswordAndCode(
      @NotBlank @Size(max = 72) String password, @NotBlank @Size(max = 32) String code) {
    @Override
    public String toString() {
      return "MfaPasswordAndCode[redacted]";
    }
  }

  record Verify(@NotBlank @Size(max = 32) String code) {
    @Override
    public String toString() {
      return "MfaVerify[redacted]";
    }
  }

  private final MfaService mfa;
  private final MfaSignIn signIn;
  private final SessionUser account;
  private final UserSessions userSessions;
  private final SessionAuthenticationService sessions;

  MfaController(
      MfaService mfa,
      MfaSignIn signIn,
      SessionUser account,
      UserSessions userSessions,
      SessionAuthenticationService sessions) {
    this.mfa = mfa;
    this.signIn = signIn;
    this.account = account;
    this.userSessions = userSessions;
    this.sessions = sessions;
  }

  @GetMapping
  MfaService.Settings settings(HttpSession session) {
    return mfa.settings(session);
  }

  @PostMapping("/setup")
  ResponseEntity<MfaService.Setup> setup(
      @Valid @RequestBody Password request, HttpSession session) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(mfa.setup(request.password(), session));
  }

  @PostMapping("/enable")
  ResponseEntity<MfaService.Enabled> enable(
      @Valid @RequestBody PasswordAndCode request,
      HttpSession session,
      HttpServletRequest servlet,
      HttpServletResponse response) {
    var enabled = mfa.enable(request.password(), request.code(), session);
    updateSession(session, servlet, response, true);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(enabled);
  }

  @PostMapping("/disable")
  ResponseEntity<Void> disable(
      @Valid @RequestBody PasswordAndCode request,
      HttpSession session,
      HttpServletRequest servlet,
      HttpServletResponse response) {
    mfa.disable(request.password(), request.code(), session);
    updateSession(session, servlet, response, false);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/verify")
  ResponseEntity<?> verify(
      @Valid @RequestBody Verify request,
      HttpServletRequest servlet,
      HttpServletResponse response) {
    return signIn.complete(request.code(), servlet, response);
  }

  private void updateSession(
      HttpSession session,
      HttpServletRequest servlet,
      HttpServletResponse response,
      boolean verified) {
    userSessions.rotateAndRevokeOthers(servlet, account.id(session));
    sessions.signIn(account.email(session), servlet, response, verified);
  }
}
