package com.sprintflowinsight.integration;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/webhooks")
class OutboundWebhookController {
  private final OutboundWebhooks webhooks;

  OutboundWebhookController(OutboundWebhooks webhooks) {
    this.webhooks = webhooks;
  }

  @GetMapping
  List<OutboundWebhooks.Webhook> list(@PathVariable long projectId, HttpSession session) {
    return webhooks.list(projectId, session);
  }

  @PostMapping
  ResponseEntity<OutboundWebhooks.Issued> create(
      @PathVariable long projectId,
      @Valid @RequestBody OutboundWebhooks.Draft draft,
      HttpSession session) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(webhooks.create(projectId, draft, session));
  }

  @DeleteMapping("/{webhookId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void remove(@PathVariable long projectId, @PathVariable long webhookId, HttpSession session) {
    webhooks.remove(projectId, webhookId, session);
  }

  @GetMapping("/{webhookId}/deliveries")
  List<OutboundWebhooks.Delivery> deliveries(
      @PathVariable long projectId, @PathVariable long webhookId, HttpSession session) {
    return webhooks.deliveries(projectId, webhookId, session);
  }
}
