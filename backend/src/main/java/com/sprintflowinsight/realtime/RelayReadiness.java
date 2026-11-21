package com.sprintflowinsight.realtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.realtime.relay.enabled", havingValue = "true")
class RelayReadiness implements HealthIndicator {
  private volatile boolean available;

  @EventListener
  void brokerAvailability(BrokerAvailabilityEvent event) {
    available = event.isBrokerAvailable();
  }

  @Override
  public Health health() {
    return available ? Health.up().build() : Health.outOfService().build();
  }
}
