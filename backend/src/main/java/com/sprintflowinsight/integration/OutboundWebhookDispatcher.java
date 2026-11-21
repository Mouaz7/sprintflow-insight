package com.sprintflowinsight.integration;

import com.sprintflowinsight.common.crypto.EncryptionException;
import com.sprintflowinsight.common.crypto.EncryptionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "app.webhooks.dispatch.enabled",
    havingValue = "true",
    matchIfMissing = true)
class OutboundWebhookDispatcher {
  private final OutboundWebhookQueue queue;
  private final OutboundWebhookTransport transport;
  private final EncryptionService encryption;

  OutboundWebhookDispatcher(
      OutboundWebhookQueue queue,
      OutboundWebhookTransport transport,
      EncryptionService encryption) {
    this.queue = queue;
    this.transport = transport;
    this.encryption = encryption;
  }

  @Bean(defaultCandidate = false)
  static ThreadPoolTaskScheduler outboundWebhookScheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setThreadNamePrefix("outbound-webhook-");
    return scheduler;
  }

  @Scheduled(
      fixedDelayString = "${app.webhooks.dispatch.delay:PT5S}",
      scheduler = "outboundWebhookScheduler")
  void dispatch() {
    for (int i = 0; i < 5; i++) {
      var claim = queue.claim();
      if (claim.isEmpty()) return;
      var item = claim.get();
      OutboundWebhookTransport.Result result;
      try {
        var url = PublicWebhookDestination.https(item.url());
        String secret =
            encryption.decrypt(item.encryptedSecret(), OutboundWebhooks.SECRET.context());
        result = transport.send(url, secret, item.id(), item.event(), item.payload());
      } catch (EncryptionException | IllegalArgumentException invalidConfiguration) {
        result = new OutboundWebhookTransport.Result(null, "REJECTED", false);
      }
      queue.finish(item, result);
    }
  }
}
