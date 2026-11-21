package com.sprintflowinsight.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sprintflowinsight.auth.UserIdentity;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.messaging.access.intercept.MessageAuthorizationContext;

/** Pure pieces of the realtime module: destinations, principals, decisions and limits. */
class RealtimeUnitTest {
  @Test
  @SuppressWarnings("unchecked")
  void openTaskPresenceCannotExposeForeignMissingOrTrashedTasks() {
    var access = mock(ProjectAccessService.class);
    var tasks = mock(com.sprintflowinsight.workspace.task.TaskRepository.class);
    var broker = mock(org.springframework.beans.factory.ObjectProvider.class);
    var sessions =
        new RealtimeSessions(
            mock(org.springframework.session.SessionRepository.class),
            access,
            mock(com.sprintflowinsight.auth.UserRepository.class),
            mock(com.sprintflowinsight.auth.MfaService.class),
            broker,
            Duration.ofSeconds(90),
            tasks);
    when(access.canRead(7L, 5L)).thenReturn(true);
    var foreign = new com.sprintflowinsight.workspace.task.Task();
    foreign.setProjectId(8L);
    var trashed = new com.sprintflowinsight.workspace.task.Task();
    trashed.setProjectId(7L);
    trashed.setTrashedAt(java.time.Instant.now());
    when(tasks.findById(11L)).thenReturn(java.util.Optional.of(foreign));
    when(tasks.findById(12L)).thenReturn(java.util.Optional.of(trashed));
    for (Long taskId : java.util.List.of(11L, 12L, 13L))
      assertThatThrownBy(() -> sessions.openTask("s", 7, taskId, signedIn(5)))
          .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThatThrownBy(() -> sessions.openTask("s", 7, 11L, signedIn(6)))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verifyNoInteractions(broker);
  }

  private static UsernamePasswordAuthenticationToken signedIn(long userId) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new UserIdentity(userId, "u@example.com"),
        null,
        List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }

  @Test
  void topicsAreParsedStrictly() {
    assertThat(ProjectTopics.events(12)).isEqualTo("/topic/projects/12");
    assertThat(ProjectTopics.presence(12)).isEqualTo("/topic/projects/12/presence");
    assertThat(ProjectTopics.eventsProject("/topic/projects/12")).hasValue(12);
    assertThat(ProjectTopics.eventsProject("/topic/projects/12/presence")).isEmpty();
    assertThat(ProjectTopics.project("/topic/projects/12/presence")).hasValue(12);
    assertThat(ProjectTopics.project("/topic/projects/12")).hasValue(12);
    for (String other :
        new String[] {
          null,
          "/topic/projects/",
          "/topic/projects/0",
          "/topic/projects/-1",
          "/topic/projects/1x",
          "/topic/projects/1/other",
          "/topic/projects/12345678901234567890",
          "/topic/other/1",
          "/presence"
        }) assertThat(ProjectTopics.project(other)).as(other).isEmpty();
  }

  @Test
  void onlyAuthenticatedSessionIdentitiesHaveAUserId() {
    assertThat(RealtimeSecurityConfiguration.userId(signedIn(5))).isEqualTo(5L);
    assertThat(RealtimeSecurityConfiguration.userId(null)).isNull();
    assertThat(
            RealtimeSecurityConfiguration.userId(
                new AnonymousAuthenticationToken(
                    "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))))
        .isNull();
    assertThat(
            RealtimeSecurityConfiguration.userId(
                UsernamePasswordAuthenticationToken.unauthenticated(
                    new UserIdentity(5L, "u@example.com"), null)))
        .isNull();
    assertThat(
            RealtimeSecurityConfiguration.userId(
                UsernamePasswordAuthenticationToken.authenticated("someone", null, List.of())))
        .isNull();
    assertThat(RealtimeSecurityConfiguration.userId(() -> "plain principal")).isNull();
  }

  @Test
  void subscriptionsNeedReadAccessToTheProjectInTheDestination() {
    ProjectAccessService access = mock(ProjectAccessService.class);
    when(access.canRead(7L, 5L)).thenReturn(true);
    var reader = RealtimeSecurityConfiguration.projectReader(access);
    var message = MessageBuilder.withPayload(new byte[0]).build();

    assertThat(
            reader
                .authorize(
                    () -> signedIn(5),
                    new MessageAuthorizationContext<>(message, Map.of("projectId", "7")))
                .isGranted())
        .isTrue();
    assertThat(
            reader
                .authorize(
                    () -> signedIn(6),
                    new MessageAuthorizationContext<>(message, Map.of("projectId", "7")))
                .isGranted())
        .isFalse();
    for (String invalid : new String[] {"0", "07", "x", "99999999999999999999"})
      assertThat(
              reader
                  .authorize(
                      () -> signedIn(5),
                      new MessageAuthorizationContext<>(message, Map.of("projectId", invalid)))
                  .isGranted())
          .as(invalid)
          .isFalse();
    assertThat(
            reader
                .authorize(() -> signedIn(5), new MessageAuthorizationContext<>(message, Map.of()))
                .isGranted())
        .isFalse();
    assertThat(
            reader
                .authorize(
                    () -> null,
                    new MessageAuthorizationContext<>(message, Map.of("projectId", "7")))
                .isGranted())
        .isFalse();
  }

  @Test
  void theSubscriptionLimitOnlyStopsNewSubscriptionsBeyondTheCap() {
    RealtimeSessions sessions = mock(RealtimeSessions.class);
    var limit = new SubscriptionLimit(sessions);
    when(sessions.subscriptions("full")).thenReturn(SubscriptionLimit.MAX_SUBSCRIPTIONS);
    when(sessions.subscriptions("room")).thenReturn(SubscriptionLimit.MAX_SUBSCRIPTIONS - 1);

    var subscribeFull = frame(StompCommand.SUBSCRIBE, "full");
    assertThatThrownBy(() -> limit.preSend(subscribeFull, null))
        .isInstanceOf(MessageDeliveryException.class);
    var subscribe = frame(StompCommand.SUBSCRIBE, "room");
    assertThat(limit.preSend(subscribe, null)).isSameAs(subscribe);
    var send = frame(StompCommand.SEND, "full");
    assertThat(limit.preSend(send, null)).isSameAs(send);
  }

  private static org.springframework.messaging.Message<byte[]> frame(
      StompCommand command, String session) {
    var headers = StompHeaderAccessor.create(command);
    headers.setSessionId(session);
    headers.setDestination("/topic/projects/1");
    return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
  }

  @Test
  void retentionSettingsAreValidated() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    assertThatThrownBy(
            () -> new RealtimeEventLog(jdbc, RealtimeEventLog.PAGE_SIZE - 1, Duration.ofDays(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RealtimeEventLog(jdbc, 1000, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RealtimeEventLog(jdbc, 1000, Duration.ofDays(-1)))
        .isInstanceOf(IllegalArgumentException.class);
    new RealtimeEventLog(jdbc, RealtimeEventLog.PAGE_SIZE, Duration.ofSeconds(1));
    verifyNoInteractions(jdbc);
  }

  @Test
  void publishingWithoutAProjectOrSourceIdDoesNothing() {
    RealtimeEventLog log = mock(RealtimeEventLog.class);
    var publisher = new RealtimePublisher(log, null);
    publisher.publish(null, 1L, "TaskMoved", 1L, 1L, java.time.Instant.now());
    publisher.publish(
        java.util.UUID.randomUUID(), null, "TaskMoved", 1L, 1L, java.time.Instant.now());
    verifyNoInteractions(log);
  }
}
