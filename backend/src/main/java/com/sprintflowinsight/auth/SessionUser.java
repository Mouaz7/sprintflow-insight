package com.sprintflowinsight.auth;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SessionUser {
  private static final String EMAIL_ATTRIBUTE = SessionUser.class.getName() + ".email.";
  private final UserRepository users;

  public SessionUser(UserRepository users) {
    this.users = users;
  }

  /**
   * The signed-in user's current email. Looked up by immutable id (so email changes take effect
   * immediately) and memoised for the rest of the HTTP request.
   */
  public String email(HttpSession session) {
    Long id = id(session);
    RequestAttributes request = RequestContextHolder.getRequestAttributes();
    String key = EMAIL_ATTRIBUTE + id;
    if (request != null
        && request.getAttribute(key, RequestAttributes.SCOPE_REQUEST) instanceof String cached)
      return cached;
    String email =
        users
            .findById(id)
            .map(User::getEmail)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Sign in again to continue."));
    if (request != null) request.setAttribute(key, email, RequestAttributes.SCOPE_REQUEST);
    return email;
  }

  public boolean mfaVerified() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.getPrincipal() instanceof UserIdentity user
        && user.mfaVerified();
  }

  java.time.Instant authenticatedAt() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.getPrincipal() instanceof UserIdentity user
        ? java.time.Instant.ofEpochMilli(user.authenticatedAtMillis())
        : java.time.Instant.EPOCH;
  }

  public Long id(HttpSession session) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
    if (!(authentication.getPrincipal() instanceof UserIdentity identity)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again to continue.");
    }
    return identity.userId();
  }
}
