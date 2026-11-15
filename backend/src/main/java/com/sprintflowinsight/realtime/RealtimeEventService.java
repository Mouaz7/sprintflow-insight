package com.sprintflowinsight.realtime;

import com.sprintflowinsight.workspace.project.ProjectAccessService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

/** Gap filling after a reconnect: the realtime messages a member missed. */
@Service
public class RealtimeEventService {
  private final ProjectAccessService access;
  private final RealtimeEventLog log;

  public RealtimeEventService(ProjectAccessService access, RealtimeEventLog log) {
    this.access = access;
    this.log = log;
  }

  public RealtimeEventPage since(Long projectId, long after, HttpSession session) {
    access.read(projectId, session);
    return log.since(projectId, after);
  }
}
