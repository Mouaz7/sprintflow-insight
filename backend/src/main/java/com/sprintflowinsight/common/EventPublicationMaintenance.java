package com.sprintflowinsight.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retries incomplete event publications, but not forever. A publication older than {@code
 * app.events.max-retry-age} is given up: it is logged once (type and id only, never the payload,
 * which may name a user) and removed from the registry. Younger ones are resubmitted once they are
 * a minute old.
 */
@Component
public class EventPublicationMaintenance {
  private static final Logger LOG = LoggerFactory.getLogger(EventPublicationMaintenance.class);
  static final Duration MIN_AGE = Duration.ofMinutes(1);

  private final IncompleteEventPublications publications;
  private final EventPublicationRepository registry;
  private final EventPublicationClaims claims;
  private final Duration maxRetryAge;
  private final Clock clock;

  @Autowired
  public EventPublicationMaintenance(
      IncompleteEventPublications publications,
      EventPublicationRepository registry,
      EventPublicationClaims claims,
      @Value("${app.events.max-retry-age:PT24H}") Duration maxRetryAge) {
    this(publications, registry, claims, maxRetryAge, Clock.systemUTC());
  }

  EventPublicationMaintenance(
      IncompleteEventPublications publications,
      EventPublicationRepository registry,
      EventPublicationClaims claims,
      Duration maxRetryAge,
      Clock clock) {
    if (maxRetryAge.compareTo(MIN_AGE) <= 0)
      throw new IllegalArgumentException("app.events.max-retry-age must exceed " + MIN_AGE + ".");
    this.publications = publications;
    this.registry = registry;
    this.claims = claims;
    this.maxRetryAge = maxRetryAge;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${app.events.retry-delay-ms:60000}")
  public void retry() {
    Instant now = clock.instant();
    Instant cutoff = now.minus(maxRetryAge);
    var expiredIds = claims.claim(cutoff, null);
    if (!expiredIds.isEmpty()) {
      var expired =
          registry.findIncompletePublicationsPublishedBefore(cutoff).stream()
              .filter(item -> expiredIds.contains(item.getIdentifier()))
              .toList();
      for (TargetEventPublication publication : expired)
        LOG.warn(
            "Giving up event publication {} of type {} after {} without completion.",
            publication.getIdentifier(),
            publication.getEvent() == null
                ? "unknown"
                : publication.getEvent().getClass().getName(),
            maxRetryAge);
      if (!expired.isEmpty())
        registry.deletePublications(
            expired.stream().map(TargetEventPublication::getIdentifier).toList());
    }
    var retryIds = claims.claim(now.minus(MIN_AGE), cutoff);
    if (!retryIds.isEmpty())
      publications.resubmitIncompletePublications(
          publication -> retryIds.contains(publication.getIdentifier()));
  }
}
