package com.sprintflowinsight.realtime;

import com.sprintflowinsight.auth.UserIdentity;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.messaging.access.intercept.MessageAuthorizationContext;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

/**
 * Frame-level authorisation. {@link EnableWebSocketSecurity} also checks the session's CSRF token
 * in the CONNECT headers. Every frame needs the authenticated session user; SUBSCRIBE is allowed
 * only to the two topics of a project the user may read, and every other destination, including
 * any SEND, is denied.
 */
@Configuration
@EnableWebSocketSecurity
public class RealtimeSecurityConfiguration {
  private static final Logger SECURITY_LOG = LoggerFactory.getLogger("security.events");

  @Bean
  AuthorizationManager<Message<?>> messageAuthorizationManager(
      MessageMatcherDelegatingAuthorizationManager.Builder messages,
      ProjectAccessService access) {
    return messages
        .nullDestMatcher()
        .authenticated()
        .simpSubscribeDestMatchers(ProjectTopics.EVENTS, ProjectTopics.PRESENCE)
        .access(projectReader(access))
        .anyMessage()
        .denyAll()
        .build();
  }

  static AuthorizationManager<MessageAuthorizationContext<?>> projectReader(
      ProjectAccessService access) {
    return (authentication, context) -> {
      Long userId = userId(authentication.get());
      Long projectId = projectId(context.getVariables().get("projectId"));
      boolean granted = userId != null && projectId != null && access.canRead(projectId, userId);
      if (!granted) SECURITY_LOG.warn("Denied realtime subscription to project {}.", projectId);
      return new AuthorizationDecision(granted);
    };
  }

  /** The account id of an authenticated session principal, or {@code null}. */
  static Long userId(Principal principal) {
    if (principal instanceof AnonymousAuthenticationToken) return null;
    if (principal instanceof Authentication authentication
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof UserIdentity identity) return identity.userId();
    return null;
  }

  private static Long projectId(String value) {
    if (value == null || !value.matches("[1-9][0-9]{0,17}")) return null;
    return Long.valueOf(value);
  }
}
