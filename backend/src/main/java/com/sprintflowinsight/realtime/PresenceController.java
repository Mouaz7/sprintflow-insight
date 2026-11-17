package com.sprintflowinsight.realtime;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class PresenceController {
  private final RealtimeSessions sessions;

  public PresenceController(RealtimeSessions sessions) {
    this.sessions = sessions;
  }

  @MessageMapping("/projects/{projectId}/presence")
  public void openTask(
      @DestinationVariable long projectId,
      @Valid @Payload OpenTaskRequest request,
      SimpMessageHeaderAccessor headers,
      Principal user) {
    sessions.openTask(headers.getSessionId(), projectId, request.taskId(), user);
  }
}
