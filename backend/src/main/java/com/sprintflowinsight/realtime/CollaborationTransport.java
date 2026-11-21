package com.sprintflowinsight.realtime;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.crdt.CrdtFrames;
import com.sprintflowinsight.crdt.CrdtService;
import com.sprintflowinsight.crdt.CrdtTarget;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.util.MimeTypeUtils;

@Controller
class CollaborationTransport {
  private static final int UUID_BYTES = Long.BYTES * 2;
  private final CrdtService collaboration;
  private final SimpMessagingTemplate messaging;

  CollaborationTransport(CrdtService collaboration, SimpMessagingTemplate messaging) {
    this.collaboration = collaboration;
    this.messaging = messaging;
  }

  record Caret(@Size(max = 256) byte[] anchor, @Size(max = 256) byte[] head) {}

  record Presence(
      String sessionId, long userId, String name, byte[] anchor, byte[] head, Instant at) {}

  @MessageMapping("/projects/{projectId}/collaboration/{target}/{id}/updates")
  public void update(
      @DestinationVariable long projectId,
      @DestinationVariable CrdtTarget target,
      @DestinationVariable long id,
      @Payload byte[] payload,
      Principal principal) {
    long user = account(principal);
    if (payload.length <= UUID_BYTES || payload.length > UUID_BYTES + CrdtFrames.MAX_UPDATE_BYTES)
      throw ApiException.badRequest("Invalid collaboration frame size.");
    var frame = ByteBuffer.wrap(payload);
    UUID updateId = new UUID(frame.getLong(), frame.getLong());
    byte[] update = new byte[frame.remaining()];
    frame.get(update);
    var applied = collaboration.appendFromSocket(projectId, target, id, user, updateId, update);
    byte[] outgoing =
        ByteBuffer.allocate(Long.BYTES + payload.length)
            .putLong(applied.revision())
            .put(payload)
            .array();
    var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
    headers.setContentType(MimeTypeUtils.APPLICATION_OCTET_STREAM);
    messaging.convertAndSend(
        ProjectTopics.collaboration(projectId, target.name(), id, "updates"),
        outgoing,
        headers.getMessageHeaders());
  }

  @MessageMapping("/projects/{projectId}/collaboration/{target}/{id}/awareness")
  public void awareness(
      @DestinationVariable long projectId,
      @DestinationVariable CrdtTarget target,
      @DestinationVariable long id,
      @Valid @Payload Caret caret,
      SimpMessageHeaderAccessor headers,
      Principal principal) {
    long user = account(principal);
    String name = collaboration.participant(projectId, target, id, user);
    messaging.convertAndSend(
        ProjectTopics.collaboration(projectId, target.name(), id, "awareness"),
        new Presence(
            headers.getSessionId(), user, name, caret.anchor(), caret.head(), Instant.now()));
  }

  private static long account(Principal principal) {
    Long user = RealtimeSecurityConfiguration.userId(principal);
    if (user == null) throw ApiException.forbidden("Sign in to edit shared text.");
    return user;
  }
}
