package com.sprintflowinsight.auth;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SessionUser {
  private final UserRepository users;

  public SessionUser(UserRepository users) {
    this.users = users;
  }

  public String email(HttpSession session) {
    return users
        .findById(id(session))
        .map(User::getEmail)
        .orElseThrow(
            () ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again to continue."));
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
