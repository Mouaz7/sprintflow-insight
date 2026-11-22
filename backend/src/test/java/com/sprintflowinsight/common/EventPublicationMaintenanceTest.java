package com.sprintflowinsight.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.TargetEventPublication;

class EventPublicationMaintenanceTest {
  private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
  private final IncompleteEventPublications publications = mock(IncompleteEventPublications.class);
  private final EventPublicationRepository registry = mock(EventPublicationRepository.class);
  private final EventPublicationClaims claims = mock(EventPublicationClaims.class);
  private final EventPublicationMaintenance maintenance =
      new EventPublicationMaintenance(
          publications, registry, claims, Duration.ofHours(24), Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void givesUpPublicationsOlderThanTheMaxRetryAge() {
    UUID expiredId = UUID.randomUUID();
    var expired = mock(TargetEventPublication.class);
    when(expired.getIdentifier()).thenReturn(expiredId);
    when(expired.getEvent()).thenReturn("payload");
    when(claims.claim(NOW.minus(Duration.ofHours(24)), null)).thenReturn(Set.of(expiredId));
    when(registry.findIncompletePublicationsPublishedBefore(NOW.minus(Duration.ofHours(24))))
        .thenReturn(List.of(expired));

    maintenance.retry();

    verify(registry).deletePublications(List.of(expiredId));
  }

  @Test
  void resubmitsOnlyClaimedPublicationsBetweenOneMinuteAndTheMaxRetryAge() {
    UUID older = UUID.randomUUID();
    UUID oldest = UUID.randomUUID();
    when(claims.claim(NOW.minus(Duration.ofMinutes(1)), NOW.minus(Duration.ofHours(24))))
        .thenReturn(Set.of(older, oldest));

    maintenance.retry();

    verify(registry, never()).deletePublications(any());
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Predicate<EventPublication>> filter = ArgumentCaptor.forClass(Predicate.class);
    verify(publications).resubmitIncompletePublications(filter.capture());
    assertThat(filter.getValue().test(event(UUID.randomUUID()))).isFalse();
    assertThat(filter.getValue().test(event(older))).isTrue();
    assertThat(filter.getValue().test(event(oldest))).isTrue();
  }

  @Test
  void rejectsAMaxRetryAgeThatWouldNeverRetry() {
    assertThatThrownBy(
            () ->
                new EventPublicationMaintenance(
                    publications, registry, claims, Duration.ofSeconds(30), Clock.systemUTC()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static EventPublication event(UUID id) {
    var publication = mock(EventPublication.class);
    when(publication.getIdentifier()).thenReturn(id);
    return publication;
  }
}
