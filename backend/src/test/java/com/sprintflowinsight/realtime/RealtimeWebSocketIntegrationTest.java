package com.sprintflowinsight.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.sprintflowinsight.support.HttpFlowTest;
import com.sprintflowinsight.workspace.task.Task;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * STOMP over a real WebSocket against the running server: handshake authentication, CSRF and Origin
 * checks, subscription authorisation, sequence numbers, rollback and gap filling.
 */
class RealtimeWebSocketIntegrationTest extends HttpFlowTest {
  private static final Duration WAIT = Duration.ofSeconds(10);

  @LocalServerPort int port;
  @Autowired SimpleBrokerMessageHandler broker;
  @Autowired RealtimeSessions realtimeSessions;

  private final WebSocketStompClient client = client();
  private final List<StompSession> opened = new java.util.ArrayList<>();

  @AfterEach
  void disconnect() {
    opened.forEach(
        session -> {
          if (session.isConnected()) session.disconnect();
        });
    client.stop();
  }

  private static WebSocketStompClient client() {
    var client = new WebSocketStompClient(new StandardWebSocketClient());
    client.setMessageConverter(
        new CompositeMessageConverter(
            List.of(new ByteArrayMessageConverter(), new JacksonJsonMessageConverter())));
    return client;
  }

  /** Records ERROR frames and transport failures of one connection. */
  static final class Probe extends StompSessionHandlerAdapter {
    final BlockingQueue<String> errors = new LinkedBlockingQueue<>();

    @Override
    public Type getPayloadType(StompHeaders headers) {
      return byte[].class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
      errors.add("ERROR " + headers.getFirst("message"));
    }

    @Override
    public void handleException(
        StompSession session,
        org.springframework.messaging.simp.stomp.StompCommand command,
        StompHeaders headers,
        byte[] payload,
        Throwable exception) {
      errors.add("CLIENT " + command + " " + exception);
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
      errors.add("TRANSPORT " + exception);
    }
  }

  /** Collects JSON messages of one subscription. */
  static final class Inbox implements StompFrameHandler {
    final BlockingQueue<Map<String, Object>> messages = new LinkedBlockingQueue<>();

    @Override
    public Type getPayloadType(StompHeaders headers) {
      return Map.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handleFrame(StompHeaders headers, Object payload) {
      messages.add((Map<String, Object>) payload);
    }

    Map<String, Object> next(String type) throws InterruptedException {
      long deadline = System.nanoTime() + WAIT.toNanos();
      while (System.nanoTime() < deadline) {
        var message = messages.poll(200, TimeUnit.MILLISECONDS);
        if (message != null && (type == null || type.equals(message.get("type")))) return message;
      }
      throw new AssertionError("No " + type + " message arrived.");
    }
  }

  private String url() {
    return "ws://localhost:" + port + "/ws";
  }

  private StompSession connect(String cookie, Probe probe) throws Exception {
    return connectAsync(cookie, true, null, probe).get(WAIT.toSeconds(), TimeUnit.SECONDS);
  }

  private java.util.concurrent.CompletableFuture<StompSession> connectAsync(
      String cookie, boolean withCsrf, String origin, Probe probe) {
    String previous = sessionCookie;
    sessionCookie = cookie;
    var handshake = new WebSocketHttpHeaders();
    if (cookie != null) handshake.add(HttpHeaders.COOKIE, cookie);
    if (origin != null) handshake.add(HttpHeaders.ORIGIN, origin);
    var connect = new StompHeaders();
    if (withCsrf) {
      HttpHeaders csrf = csrfHeaders();
      connect.add("X-CSRF-TOKEN", csrf.getFirst("X-CSRF-TOKEN"));
    }
    sessionCookie = previous;
    var future = client.connectAsync(url(), handshake, connect, probe);
    future.thenAccept(opened::add);
    return future;
  }

  /** Subscribes and waits until the broker has registered the subscription. */
  private Inbox subscribe(StompSession session, String destination) {
    Inbox inbox = new Inbox();
    var subscription = session.subscribe(destination, inbox);
    await()
        .atMost(WAIT)
        .until(
            () -> {
              var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
              headers.setDestination(destination);
              var probe = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
              return broker.getSubscriptionRegistry().findSubscriptions(probe).values().stream()
                  .anyMatch(ids -> ids.contains(subscription.getSubscriptionId()));
            });
    return inbox;
  }

  private String register() {
    registerTestUser();
    return sessionCookie;
  }

  private long latestSeq(long projectId) {
    return jdbc
        .queryForList(
            "select last_seq from realtime_sequence where project_id=?", Long.class, projectId)
        .stream()
        .findFirst()
        .orElse(0L);
  }

  private long typed(long projectId, String type, long entityId) {
    return jdbc.queryForObject(
        "select count(*) from realtime_event where project_id=? and type=? and entity_id=?",
        Long.class,
        projectId,
        type,
        entityId);
  }

  private long createTask(long projectId) {
    long taskId =
        id(
            request(
                HttpMethod.POST,
                "/api/tasks",
                Map.of("title", "Live task", "status", "To Do", "projectId", projectId)));
    await().atMost(WAIT).until(() -> typed(projectId, "TaskCreated", taskId) == 1);
    return taskId;
  }

  private Map<String, Object> move(long projectId, long taskId, String status) {
    var response =
        request(
            HttpMethod.PUT,
            "/api/tasks/" + taskId,
            Map.of("title", "Live task", "status", status, "projectId", projectId));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  private void addMember(long projectId, String email, String role) {
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/projects/" + projectId + "/members",
                    Map.of("email", email, "role", role))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void handshakeWithoutSessionIsRejected() {
    assertThatThrownBy(
            () ->
                connectAsync(null, false, null, new Probe())
                    .get(WAIT.toSeconds(), TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class);
  }

  @Test
  void handshakeFromAnotherOriginIsRejected() {
    String cookie = register();
    assertThatThrownBy(
            () ->
                connectAsync(cookie, true, "https://attacker.example", new Probe())
                    .get(WAIT.toSeconds(), TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class);
  }

  @Test
  void connectWithoutCsrfTokenIsRejected() throws Exception {
    String cookie = register();
    Probe probe = new Probe();
    var future = connectAsync(cookie, false, null, probe);
    assertThat(probe.errors.poll(WAIT.toSeconds(), TimeUnit.SECONDS)).isNotNull();
    assertThat(future.isDone() && !future.isCompletedExceptionally()).isFalse();
  }

  @Test
  void outsidersCannotSubscribeAndReceiveNothing() throws Exception {
    String owner = register();
    long ownerId = userRepository.findByEmail(testEmail).orElseThrow().getId();
    long projectId =
        id(
            request(
                HttpMethod.POST,
                "/api/projects",
                Map.of("name", "Private " + port + System.nanoTime())));
    long taskId = createTask(projectId);
    StompSession ownerSession = connect(owner, new Probe());
    Inbox presence = subscribe(ownerSession, ProjectTopics.presence(projectId));
    subscribe(ownerSession, ProjectTopics.events(projectId));

    String outsider = register();
    Probe probe = new Probe();
    StompSession session = connect(outsider, probe);
    Inbox inbox = new Inbox();
    session.subscribe(ProjectTopics.events(projectId), inbox);
    assertThat(probe.errors.poll(WAIT.toSeconds(), TimeUnit.SECONDS)).startsWith("ERROR");
    ownerSession.send("/app/projects/" + projectId + "/presence", Map.of("taskId", taskId));
    assertThat(viewerIds(awaitTaskPresence(presence, ownerId, List.of(taskId))))
        .containsExactly(ownerId);

    sessionCookie = owner;
    move(projectId, taskId, "Done");
    await().atMost(WAIT).until(() -> typed(projectId, "TaskMoved", taskId) == 1);
    assertThat(inbox.messages.poll(1, TimeUnit.SECONDS)).isNull();
  }

  @Test
  void sendingToApplicationDestinationsIsDenied() throws Exception {
    String cookie = register();
    Probe probe = new Probe();
    StompSession session = connect(cookie, probe);
    session.send("/app/anything", Map.of("hello", "world"));
    assertThat(probe.errors.poll(WAIT.toSeconds(), TimeUnit.SECONDS)).startsWith("ERROR");
  }

  @Test
  void applicationPresenceDestinationCannotBeSubscribedTo() throws Exception {
    String cookie = register();
    long projectId =
        id(
            request(
                HttpMethod.POST,
                "/api/projects",
                Map.of("name", "Subscribe " + System.nanoTime())));
    Probe probe = new Probe();
    StompSession session = connect(cookie, probe);
    session.subscribe("/app/projects/" + projectId + "/presence", new Inbox());
    assertThat(probe.errors.poll(WAIT.toSeconds(), TimeUnit.SECONDS)).startsWith("ERROR");
  }

  @Test
  void outsidersCannotSendOpenTaskPresence() throws Exception {
    register();
    long projectId =
        id(
            request(
                HttpMethod.POST,
                "/api/projects",
                Map.of("name", "Presence ACL " + System.nanoTime())));
    String outsider = register();
    Probe probe = new Probe();
    StompSession session = connect(outsider, probe);
    session.send("/app/projects/" + projectId + "/presence", Map.of("taskId", 1));
    assertThat(probe.errors.poll(WAIT.toSeconds(), TimeUnit.SECONDS)).startsWith("ERROR");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Owner", "Admin", "Member", "Viewer", "Archived"})
  void readersCanReportAndClearTheOpenTask(String role) throws Exception {
    String owner = register();
    long projectId =
        id(
            request(
                HttpMethod.POST,
                "/api/projects",
                Map.of("name", "Task presence " + role + System.nanoTime())));
    long taskId = createTask(projectId);
    String reader = owner;
    if (!role.equals("Owner")) {
      reader = register();
      String email = testEmail;
      sessionCookie = owner;
      addMember(projectId, email, role.equals("Archived") ? "Viewer" : role);
    }
    long readerId = userRepository.findByEmail(testEmail).orElseThrow().getId();
    if (role.equals("Archived"))
      jdbc.update("update projects set archived_at=now() where id=?", projectId);
    StompSession session = connect(reader, new Probe());
    Inbox presence = subscribe(session, ProjectTopics.presence(projectId));
    subscribe(session, ProjectTopics.events(projectId));
    session.send("/app/projects/" + projectId + "/presence", Map.of("taskId", taskId));
    awaitTaskPresence(presence, readerId, List.of(taskId));
    session.send(
        "/app/projects/" + projectId + "/presence",
        java.util.Collections.singletonMap("taskId", null));
    awaitTaskPresence(presence, readerId, List.of());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> awaitTaskPresence(Inbox inbox, long userId, List<Long> expected) {
    return await()
        .atMost(WAIT)
        .until(
            () -> {
              Map<String, Object> message = inbox.messages.poll(200, TimeUnit.MILLISECONDS);
              if (message == null) return null;
              var viewers = (List<Map<String, Object>>) message.get("viewers");
              var viewer =
                  viewers.stream()
                      .filter(v -> ((Number) v.get("userId")).longValue() == userId)
                      .findFirst();
              if (viewer.isEmpty()) return null;
              var taskIds =
                  ((List<Number>) viewer.orElseThrow().get("taskIds"))
                      .stream().map(Number::longValue).toList();
              return taskIds.equals(expected) ? message : null;
            },
            java.util.Objects::nonNull);
  }

  @Test
  void memberReceivesTaskMovedWithTheNextSequenceNumber() throws Exception {
    String owner = register();
    long projectId =
        id(request(HttpMethod.POST, "/api/projects", Map.of("name", "Live " + System.nanoTime())));
    long taskId = createTask(projectId);
    String member = register();
    String memberEmail = testEmail;
    sessionCookie = owner;
    addMember(projectId, memberEmail, "Viewer");

    StompSession session = connect(member, new Probe());
    Inbox inbox = subscribe(session, ProjectTopics.events(projectId));
    long before = latestSeq(projectId);

    sessionCookie = owner;
    Map<String, Object> saved = move(projectId, taskId, "In Progress");

    Map<String, Object> message = inbox.next("TaskMoved");
    assertThat(message)
        .containsEntry("projectId", (int) projectId)
        .containsEntry("seq", (int) (before + 1))
        .containsEntry("entityId", (int) taskId);
    assertThat(((Number) message.get("version")).longValue())
        .isEqualTo(((Number) saved.get("version")).longValue());
    assertThat(message).containsOnlyKeys("projectId", "seq", "type", "entityId", "version");
  }

  @Test
  void rolledBackChangesSendNothing() throws Exception {
    register();
    long projectId =
        id(
            request(
                HttpMethod.POST, "/api/projects", Map.of("name", "Rollback " + System.nanoTime())));
    long taskId = createTask(projectId);
    StompSession session = connect(sessionCookie, new Probe());
    Inbox inbox = subscribe(session, ProjectTopics.events(projectId));
    long before = latestSeq(projectId);

    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              Task task = taskRepository.findById(taskId).orElseThrow();
              Task changes = new Task("Live task", "", "Done");
              changes.setProjectId(projectId);
              taskService.updateTask(task, changes, testEmail);
              tx.setRollbackOnly();
            });

    assertThat(inbox.messages.poll(2, TimeUnit.SECONDS)).isNull();
    assertThat(typed(projectId, "TaskMoved", taskId)).isZero();
    assertThat(latestSeq(projectId)).isEqualTo(before);
  }

  @Test
  void gapFillReturnsWhatADisconnectedMemberMissed() throws Exception {
    String owner = register();
    long projectId =
        id(request(HttpMethod.POST, "/api/projects", Map.of("name", "Gap " + System.nanoTime())));
    long taskId = createTask(projectId);
    String member = register();
    String memberEmail = testEmail;
    sessionCookie = owner;
    addMember(projectId, memberEmail, "Member");

    StompSession session = connect(member, new Probe());
    Inbox inbox = subscribe(session, ProjectTopics.events(projectId));
    sessionCookie = owner;
    move(projectId, taskId, "In Progress");
    long seen = ((Number) inbox.next("TaskMoved").get("seq")).longValue();
    session.disconnect();

    move(projectId, taskId, "Review");
    move(projectId, taskId, "Done");
    await().atMost(WAIT).until(() -> latestSeq(projectId) == seen + 2);

    sessionCookie = member;
    var page =
        request(HttpMethod.GET, "/api/projects/" + projectId + "/events?after=" + seen, null);
    assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(page.getBody())
        .containsEntry("latestSeq", (int) (seen + 2))
        .containsEntry("resync", false);
    @SuppressWarnings("unchecked")
    var events = (List<Map<String, Object>>) page.getBody().get("events");
    assertThat(events)
        .extracting(event -> event.get("seq"), event -> event.get("type"))
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple((int) (seen + 1), "TaskMoved"),
            org.assertj.core.groups.Tuple.tuple((int) (seen + 2), "TaskMoved"));
  }

  @Test
  void presenceListsEveryViewerOfTheProject() throws Exception {
    String owner = register();
    long ownerId = userRepository.findByEmail(testEmail).orElseThrow().getId();
    long projectId =
        id(
            request(
                HttpMethod.POST, "/api/projects", Map.of("name", "Presence " + System.nanoTime())));
    String member = register();
    long memberId = userRepository.findByEmail(testEmail).orElseThrow().getId();
    sessionCookie = owner;
    addMember(projectId, testEmail, "Member");

    StompSession ownerSession = connect(owner, new Probe());
    Inbox presence = subscribe(ownerSession, ProjectTopics.presence(projectId));
    subscribe(ownerSession, ProjectTopics.events(projectId));
    StompSession memberSession = connect(member, new Probe());
    subscribe(memberSession, ProjectTopics.events(projectId));

    await()
        .atMost(WAIT)
        .until(
            () -> {
              var latest = presence.messages.poll(200, TimeUnit.MILLISECONDS);
              return latest != null && viewerIds(latest).containsAll(List.of(ownerId, memberId));
            });

    memberSession.disconnect();
    await()
        .atMost(WAIT)
        .until(
            () -> {
              var latest = presence.messages.poll(200, TimeUnit.MILLISECONDS);
              return latest != null && viewerIds(latest).equals(List.of(ownerId));
            });
  }

  @SuppressWarnings("unchecked")
  private static List<Long> viewerIds(Map<String, Object> presence) {
    return ((List<Map<String, Object>>) presence.get("viewers"))
        .stream().map(viewer -> ((Number) viewer.get("userId")).longValue()).toList();
  }

  @Test
  void connectionsCloseOnceTheirSessionHasEnded() throws Exception {
    String cookie = register();
    long projectId =
        id(
            request(
                HttpMethod.POST, "/api/projects", Map.of("name", "Logout " + System.nanoTime())));
    Probe probe = new Probe();
    StompSession session = connect(cookie, probe);
    subscribe(session, ProjectTopics.events(projectId));

    realtimeSessions.sweep();
    assertThat(session.isConnected()).isTrue();

    sessionCookie = cookie;
    assertThat(request(HttpMethod.POST, "/api/auth/logout", null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    realtimeSessions.sweep();
    await().atMost(WAIT).until(() -> !session.isConnected());
  }

  @Test
  void connectionsCloseOnceTheMemberIsRemoved() throws Exception {
    String owner = register();
    long projectId =
        id(
            request(
                HttpMethod.POST, "/api/projects", Map.of("name", "Removed " + System.nanoTime())));
    String member = register();
    long memberId = userRepository.findByEmail(testEmail).orElseThrow().getId();
    sessionCookie = owner;
    addMember(projectId, testEmail, "Member");
    StompSession session = connect(member, new Probe());
    subscribe(session, ProjectTopics.events(projectId));

    jdbc.update("delete from project_member where project_id=? and user_id=?", projectId, memberId);
    realtimeSessions.sweep();
    await().atMost(WAIT).until(() -> !session.isConnected());
  }
}
