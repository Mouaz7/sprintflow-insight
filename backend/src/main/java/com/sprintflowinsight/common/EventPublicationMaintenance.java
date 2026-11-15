package com.sprintflowinsight.common;

import java.time.Duration;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EventPublicationMaintenance {
  private final IncompleteEventPublications publications;

  public EventPublicationMaintenance(IncompleteEventPublications publications) {
    this.publications = publications;
  }

  @Scheduled(fixedDelayString = "${app.events.retry-delay-ms:60000}")
  public void retry() {
    publications.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(1));
  }
}
