package com.sprintflowinsight.workspace.poker;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Planning poker for a project's stories (B7). Rules live in {@link PokerService}. */
@RestController
@RequestMapping("/api/projects/{projectId}/poker-sessions")
public class PokerController {
  private final PokerService poker;

  public PokerController(PokerService poker) {
    this.poker = poker;
  }

  /** Open sessions, or with {@code closed=true} the most recently closed ones. */
  @GetMapping
  public List<PokerSessionSummary> list(
      @PathVariable Long projectId,
      @RequestParam(defaultValue = "false") boolean closed,
      HttpSession session) {
    return poker.list(projectId, closed, session);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PokerSessionView start(
      @PathVariable Long projectId,
      @Valid @RequestBody StartPokerRequest request,
      HttpSession session) {
    return poker.start(projectId, request, session);
  }

  @GetMapping("/{sessionId}")
  public PokerSessionView get(
      @PathVariable Long projectId, @PathVariable Long sessionId, HttpSession session) {
    return poker.get(projectId, sessionId, session);
  }

  @PutMapping("/{sessionId}/vote")
  public PokerSessionView vote(
      @PathVariable Long projectId,
      @PathVariable Long sessionId,
      @Valid @RequestBody VoteRequest request,
      HttpSession session) {
    return poker.vote(projectId, sessionId, request, session);
  }

  @DeleteMapping("/{sessionId}/vote")
  public PokerSessionView retract(
      @PathVariable Long projectId,
      @PathVariable Long sessionId,
      @Valid @RequestBody PokerRoundRequest request,
      HttpSession session) {
    return poker.retract(projectId, sessionId, request, session);
  }

  @PostMapping("/{sessionId}/reveal")
  public PokerSessionView reveal(
      @PathVariable Long projectId,
      @PathVariable Long sessionId,
      @Valid @RequestBody PokerRoundRequest request,
      HttpSession session) {
    return poker.reveal(projectId, sessionId, request, session);
  }

  @PostMapping("/{sessionId}/revote")
  public PokerSessionView revote(
      @PathVariable Long projectId,
      @PathVariable Long sessionId,
      @Valid @RequestBody PokerRoundRequest request,
      HttpSession session) {
    return poker.revote(projectId, sessionId, request, session);
  }

  @PostMapping("/{sessionId}/accept")
  public PokerSessionView accept(
      @PathVariable Long projectId,
      @PathVariable Long sessionId,
      @Valid @RequestBody AcceptPokerRequest request,
      HttpSession session) {
    return poker.accept(projectId, sessionId, request, session);
  }

  @DeleteMapping("/{sessionId}")
  public PokerSessionView cancel(
      @PathVariable Long projectId,
      @PathVariable Long sessionId,
      @Valid @RequestBody PokerRoundRequest request,
      HttpSession session) {
    return poker.cancel(projectId, sessionId, request, session);
  }
}
