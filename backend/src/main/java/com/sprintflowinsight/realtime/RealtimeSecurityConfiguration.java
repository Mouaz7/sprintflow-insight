package com.sprintflowinsight.realtime;

import com.sprintflowinsight.auth.MfaService;
import com.sprintflowinsight.auth.UserIdentity;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.crdt.CrdtService;
import com.sprintflowinsight.crdt.CrdtTarget;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
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
 * only to topics of a project the user may read. Shared-text sends require current edit access.
 */
@Configuration
@EnableWebSocketSecurity
public class RealtimeSecurityConfiguration {
  private static final Logger SECURITY_LOG = LoggerFactory.getLogger("security.events");

  @Bean
  AuthorizationManager<Message<?>> messageAuthorizationManager(
      MessageMatcherDelegatingAuthorizationManager.Builder messages,
      ProjectAccessService access,
      CrdtService collaboration,
      MfaService mfa) {
    return messages
        .nullDestMatcher()
        .authenticated()
        .simpSubscribeDestMatchers(ProjectTopics.EVENTS, ProjectTopics.PRESENCE)
        .access(requireMfa(projectReader(access), mfa))
        .simpSubscribeDestMatchers(ProjectTopics.CRDT_UPDATES, ProjectTopics.CRDT_AWARENESS)
        .access(requireMfa(collaborationReader(access), mfa))
        .simpSubscribeDestMatchers("/**")
        .denyAll()
        .simpDestMatchers(ProjectTopics.OPEN_TASK)
        .access(requireMfa(projectReader(access), mfa))
        .simpDestMatchers(ProjectTopics.CRDT_SEND, ProjectTopics.CRDT_CARET)
        .access(requireMfa(collaborationWriter(collaboration), mfa))
        .anyMessage()
        .denyAll()
        .build();
  }

  private static AuthorizationManager<MessageAuthorizationContext<?>> requireMfa(
      AuthorizationManager<MessageAuthorizationContext<?>> next, MfaService mfa) {
    return (authentication, context) -> {
      if (!(authentication.get().getPrincipal() instanceof UserIdentity user)
          || !mfa.workspaceAllowed(user)) return new AuthorizationDecision(false);
      return next.authorize(authentication, context);
    };
  }

  private static AuthorizationManager<MessageAuthorizationContext<?>> collaborationReader(
      ProjectAccessService access) {
    return (authentication, context) -> {
      Long user = userId(authentication.get());
      var project =
          ProjectTopics.project(
              SimpMessageHeaderAccessor.getDestination(context.getMessage().getHeaders()));
      return new AuthorizationDecision(
          user != null && project.isPresent() && access.canRead(project.getAsLong(), user));
    };
  }

  private static AuthorizationManager<MessageAuthorizationContext<?>> collaborationWriter(
      CrdtService collaboration) {
    return (authentication, context) -> {
      Long user = userId(authentication.get());
      Long project = projectId(context.getVariables().get("projectId"));
      Long id = projectId(context.getVariables().get("id"));
      if (user == null || project == null || id == null) return new AuthorizationDecision(false);
      try {
        var target = CrdtTarget.valueOf(context.getVariables().getOrDefault("target", ""));
        collaboration.participant(project, target, id, user);
        return new AuthorizationDecision(true);
      } catch (ApiException | IllegalArgumentException ignored) {
        return new AuthorizationDecision(false);
      }
    };
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
