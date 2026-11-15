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
  private final EventPublicationMaintenance maintenance =
      new EventPublicationMaintenance(
          publications, registry, Duration.ofHours(24), Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void givesUpPublicationsOlderThanTheMaxRetryAge() {
    UUID expiredId = UUID.randomUUID();
    var expired = mock(TargetEventPublication.class);
    when(expired.getIdentifier()).thenReturn(expiredId);
    when(expired.getEvent()).thenReturn("payload");
    when(registry.findIncompletePublicationsPublishedBefore(NOW.minus(Duration.ofHours(24))))
        .thenReturn(List.of(expired));

    maintenance.retry();

    verify(registry).deletePublications(List.of(expiredId));
  }

  @Test
  void resubmitsOnlyPublicationsBetweenOneMinuteAndTheMaxRetryAge() {
    when(registry.findIncompletePublicationsPublishedBefore(any())).thenReturn(List.of());

    maintenance.retry();

    verify(registry, never()).deletePublications(any());
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Predicate<EventPublication>> filter = ArgumentCaptor.forClass(Predicate.class);
    verify(publications).resubmitIncompletePublications(filter.capture());
    assertThat(filter.getValue().test(publishedAgo(Duration.ofSeconds(30)))).isFalse();
    assertThat(filter.getValue().test(publishedAgo(Duration.ofMinutes(5)))).isTrue();
    assertThat(filter.getValue().test(publishedAgo(Duration.ofHours(23)))).isTrue();
    assertThat(filter.getValue().test(publishedAgo(Duration.ofHours(25)))).isFalse();
  }

  @Test
  void rejectsAMaxRetryAgeThatWouldNeverRetry() {
    assertThatThrownBy(
            () ->
                new EventPublicationMaintenance(
                    publications, registry, Duration.ofSeconds(30), Clock.systemUTC()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static EventPublication publishedAgo(Duration age) {
    var publication = mock(EventPublication.class);
    when(publication.getPublicationDate()).thenReturn(NOW.minus(age));
    return publication;
  }
}
