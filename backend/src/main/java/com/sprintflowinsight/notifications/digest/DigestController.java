package com.sprintflowinsight.notifications.digest;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class DigestController {
  private final DigestSettingsService settings;

  public DigestController(DigestSettingsService settings) {
    this.settings = settings;
  }

  @GetMapping("/api/auth/digest")
  public DigestResponse read(HttpSession session) {
    return settings.read(session);
  }

  @PutMapping("/api/auth/digest")
  public DigestResponse save(
      @Valid @RequestBody DigestRequest request,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    return settings.save(request, ifMatch, session);
  }

  @PostMapping("/api/digest/unsubscribe")
  public ResponseEntity<Void> unsubscribe(@Valid @RequestBody UnsubscribeRequest request) {
    settings.unsubscribe(request.token());
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }
}
