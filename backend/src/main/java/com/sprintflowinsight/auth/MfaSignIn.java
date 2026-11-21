package com.sprintflowinsight.auth;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
class MfaSignIn {
  private static final String CHALLENGE = MfaSignIn.class.getName() + ".challenge";

  record Required(boolean secondFactorRequired) {}

  private final MfaService mfa;
  private final SessionAuthenticationService sessions;

  MfaSignIn(MfaService mfa, SessionAuthenticationService sessions) {
    this.mfa = mfa;
    this.sessions = sessions;
  }

  ResponseEntity<?> afterPrimary(
      User user, HttpServletRequest request, HttpServletResponse response) {
    var challenge = mfa.begin(user);
    if (challenge.isEmpty()) {
      sessions.signIn(user.getEmail(), request, response);
      return ResponseEntity.ok(withWorkspaceRequirement(user, false));
    }
    var previous = request.getSession(false);
    if (previous != null) previous.invalidate();
    SecurityContextHolder.clearContext();
    request.getSession(true).setAttribute(CHALLENGE, challenge.get());
    return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(new Required(true));
  }

  User withWorkspaceRequirement(User user, boolean verified) {
    user.setMfaEnrollmentRequired(!verified && mfa.organizationRequires(user.getId()));
    return user;
  }

  ResponseEntity<?> complete(
      String code, HttpServletRequest request, HttpServletResponse response) {
    var session = request.getSession(false);
    String token =
        session != null && session.getAttribute(CHALLENGE) instanceof String value ? value : null;
    var attempt = mfa.verify(token, code);
    return switch (attempt.outcome()) {
      case ACCEPTED -> {
        session.removeAttribute(CHALLENGE);
        sessions.signIn(attempt.user().getEmail(), request, response, true);
        yield ResponseEntity.ok(attempt.user());
      }
      case EXHAUSTED -> throw MfaService.rateLimited();
      case INVALID ->
          throw new ApiException(
              ErrorCode.UNAUTHENTICATED, "The two-factor code or sign-in challenge is invalid.");
    };
  }
}
