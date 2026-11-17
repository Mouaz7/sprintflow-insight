package com.sprintflowinsight.realtime;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/events")
public class RealtimeController {
  private final RealtimeEventService events;

  public RealtimeController(RealtimeEventService events) {
    this.events = events;
  }

  @GetMapping
  public RealtimeEventPage since(
      @PathVariable Long projectId,
      @RequestParam(defaultValue = "0") @PositiveOrZero long after,
      HttpSession session) {
    return events.since(projectId, after, session);
  }
}
