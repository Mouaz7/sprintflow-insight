package com.sprintflowinsight.realtime;

import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.auth.UserRepository;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/**
 * Open WebSocket connections on this instance: presence bookkeeping and a periodic guard.
 *
 * <p>The guard closes a connection whose HTTP session has ended (logout, password change,
 * revocation or expiry) or whose user can no longer read a subscribed project, because the
 * WebSocket outlives the request that authenticated it and Spring Session JDBC publishes no
 * deletion events. A closed client reconnects through a new, fully checked handshake.
 */
@Component
public class RealtimeSessions {
  private static final Logger SECURITY_LOG = LoggerFactory.getLogger("security.events");

  private final Map<String, WebSocketSession> open = new ConcurrentHashMap<>();
  private final PresenceRegistry presence;
  private final SessionRepository<? extends Session> httpSessions;
  private final ProjectAccessService access;
  private final UserRepository users;
  private final com.sprintflowinsight.auth.MfaService mfa;
  private final ObjectProvider<SimpMessageSendingOperations> broker;
  private final Clock clock;
  private final com.sprintflowinsight.workspace.task.TaskRepository tasks;

  public RealtimeSessions(
      SessionRepository<? extends Session> httpSessions,
      ProjectAccessService access,
      UserRepository users,
      com.sprintflowinsight.auth.MfaService mfa,
      ObjectProvider<SimpMessageSendingOperations> broker,
      @Value("${app.realtime.presence-ttl:PT90S}") Duration presenceTtl,
      com.sprintflowinsight.workspace.task.TaskRepository tasks) {
    this.tasks = tasks;
    this.httpSessions = httpSessions;
    this.access = access;
    this.users = users;
    this.mfa = mfa;
    this.broker = broker;
    this.presence = new PresenceRegistry(presenceTtl);
    this.clock = Clock.systemUTC();
  }

  WebSocketHandler decorate(WebSocketHandler handler) {
    return new WebSocketHandlerDecorator(handler) {
      @Override
      public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        open.put(session.getId(), session);
        super.afterConnectionEstablished(session);
      }

      @Override
      public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
          throws Exception {
        open.remove(session.getId());
        super.afterConnectionClosed(session, status);
      }
    };
  }

  int subscriptions(String sessionId) {
    return presence.subscriptions(sessionId);
  }

  int openConnections() {
    return open.size();
  }

  @EventListener
  public void subscribed(SessionSubscribeEvent event) {
    var headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
    String destination = headers.getDestination();
    var projectId = ProjectTopics.project(destination);
    Long userId = RealtimeSecurityConfiguration.userId(event.getUser());
    if (projectId.isEmpty() || userId == null || headers.getSessionId() == null) return;
    boolean events = ProjectTopics.eventsProject(destination).isPresent();
    Set<Long> changed =
        presence.subscribed(
            headers.getSessionId(),
            headers.getSubscriptionId(),
            projectId.getAsLong(),
            events,
            userId,
            displayName(userId),
            clock.instant());
    // A new presence subscriber needs the current list even when nobody joined.
    broadcast(events ? changed : Set.of(projectId.getAsLong()));
  }

  @EventListener
  public void unsubscribed(SessionUnsubscribeEvent event) {
    var headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
    if (headers.getSessionId() == null) return;
    broadcast(presence.unsubscribed(headers.getSessionId(), headers.getSubscriptionId()));
  }

  @EventListener
  public void disconnected(SessionDisconnectEvent event) {
    broadcast(presence.disconnected(event.getSessionId()));
  }

  public void openTask(
      String sessionId, long projectId, Long taskId, java.security.Principal principal) {
    Long userId = RealtimeSecurityConfiguration.userId(principal);
    if (userId == null || !access.canRead(projectId, userId))
      throw new org.springframework.security.access.AccessDeniedException(
          "Project access required.");
    if (taskId != null
        && tasks
            .findById(taskId)
            .filter(task -> projectId == task.getProjectId() && task.getTrashedAt() == null)
            .isEmpty())
      throw new org.springframework.security.access.AccessDeniedException(
          "Task is not available in this project.");
    broadcast(presence.openTask(sessionId, projectId, taskId, clock.instant()));
  }

  /** Verifies every open connection, refreshes live presence and expires the rest. */
  @Scheduled(
      fixedDelayString = "${app.realtime.sweep-ms:30000}",
      initialDelayString = "${app.realtime.sweep-ms:30000}")
  public void sweep() {
    for (WebSocketSession session : open.values()) {
      if (!session.isOpen()) {
        open.remove(session.getId());
        continue;
      }
      if (stillAuthorised(session)) presence.touch(session.getId(), clock.instant());
      else close(session);
    }
    broadcast(presence.expire(clock.instant()));
  }

  @org.springframework.transaction.event.TransactionalEventListener
  public void organizationMfaChanged(
      com.sprintflowinsight.workspace.events.OrganizationMfaChanged event) {
    if (event.required()) sweep();
  }

  private boolean stillAuthorised(WebSocketSession session) {
    Object httpSessionId =
        session.getAttributes().get(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME);
    Long userId = RealtimeSecurityConfiguration.userId(session.getPrincipal());
    if (!(httpSessionId instanceof String id) || userId == null) return false;
    if (httpSessions.findById(id) == null) return false;
    if (session.getPrincipal()
            instanceof org.springframework.security.core.Authentication authentication
        && authentication.getPrincipal() instanceof com.sprintflowinsight.auth.UserIdentity user
        && !mfa.workspaceAllowed(user)) return false;
    for (Long projectId : presence.projects(session.getId()))
      if (!access.canRead(projectId, userId)) return false;
    return true;
  }

  private void close(WebSocketSession session) {
    SECURITY_LOG.info("Closed a realtime connection whose session or project access ended.");
    open.remove(session.getId());
    try {
      session.close(CloseStatus.POLICY_VIOLATION);
    } catch (IOException ignored) {
      // The transport is already gone; the disconnect event still clears presence.
    }
  }

  private String displayName(long userId) {
    return users
        .findById(userId)
        .map(User::getDisplayName)
        .filter(name -> !name.isBlank())
        .orElse("");
  }

  private void broadcast(Set<Long> projects) {
    if (projects.isEmpty()) return;
    SimpMessageSendingOperations messages = broker.getObject();
    for (Long projectId : projects)
      messages.convertAndSend(
          ProjectTopics.presence(projectId),
          new PresenceMessage(projectId, presence.viewers(projectId)));
  }
}
