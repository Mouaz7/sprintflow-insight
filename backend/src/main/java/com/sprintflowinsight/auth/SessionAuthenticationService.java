package com.sprintflowinsight.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class SessionAuthenticationService {
  private final SecurityContextRepository contexts;
  private final SessionAuthenticationStrategy sessions;
  private final UserRepository users;
  private final UserSessions userSessions;

  public SessionAuthenticationService(
      SecurityContextRepository contexts,
      SessionAuthenticationStrategy sessions,
      UserRepository users,
      UserSessions userSessions) {
    this.contexts = contexts;
    this.sessions = sessions;
    this.users = users;
    this.userSessions = userSessions;
  }

  public void signIn(String email, HttpServletRequest request, HttpServletResponse response) {
    signIn(email, request, response, false);
  }

  public void signIn(
      String email, HttpServletRequest request, HttpServletResponse response, boolean mfaVerified) {
    saveIdentity(email, request, response, mfaVerified, System.currentTimeMillis());
  }

  void saveIdentity(
      String email,
      HttpServletRequest request,
      HttpServletResponse response,
      boolean mfaVerified,
      long authenticatedAtMillis) {
    User user = users.findByEmail(email).orElseThrow();
    var identity =
        new UserIdentity(user.getId(), user.getEmail(), mfaVerified, authenticatedAtMillis);
    var authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            identity, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    sessions.onAuthentication(authentication, request, response);
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, request, response);
    userSessions.bind(request.getSession(), user.getId());
  }
}
