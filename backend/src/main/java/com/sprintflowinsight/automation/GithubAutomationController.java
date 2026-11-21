package com.sprintflowinsight.automation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class GithubAutomationController {
  private static final String SETTINGS = "/api/projects/{projectId}/automation/github";
  private final GithubAutomation github;

  GithubAutomationController(GithubAutomation github) {
    this.github = github;
  }

  @GetMapping(SETTINGS)
  ResponseEntity<GithubAutomation.Settings> settings(
      @PathVariable long projectId, HttpSession session) {
    return github
        .settings(projectId, session)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PutMapping(SETTINGS)
  ResponseEntity<GithubAutomation.Issued> configure(
      @PathVariable long projectId,
      @Valid @RequestBody GithubAutomation.Draft draft,
      HttpSession session) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(github.configure(projectId, draft, session));
  }

  @DeleteMapping(SETTINGS)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void disconnect(@PathVariable long projectId, HttpSession session) {
    github.disconnect(projectId, session);
  }

  @PostMapping(path = "/api/automation/github/{endpointId}", consumes = "application/json")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void receive(
      @PathVariable UUID endpointId,
      @RequestHeader("X-GitHub-Event") String event,
      @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
      HttpServletRequest request)
      throws IOException {
    int limit = GithubAutomation.MAX_PAYLOAD_BYTES;
    if (request.getContentLengthLong() > limit)
      throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE);
    byte[] body = request.getInputStream().readNBytes(limit + 1);
    if (body.length > limit) throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE);
    github.receive(endpointId, event, signature, body);
  }
}
