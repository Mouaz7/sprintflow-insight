package com.sprintflowinsight.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PresenceRegistryTest {
  private final Instant t0 = Instant.parse("2026-11-15T10:00:00Z");
  private final PresenceRegistry registry = new PresenceRegistry(Duration.ofSeconds(90));

  @Test
  void eventsSubscriptionsMakeViewersAndPresenceSubscriptionsDoNot() {
    assertThat(registry.subscribed("s1", "a", 7, false, 1, "Ada", t0)).isEmpty();
    assertThat(registry.viewers(7)).isEmpty();
    assertThat(registry.subscribed("s1", "b", 7, true, 1, "Ada", t0)).containsExactly(7L);
    assertThat(registry.viewers(7)).containsExactly(new PresenceMessage.Viewer(1, "Ada"));
    assertThat(registry.subscriptions("s1")).isEqualTo(2);
    assertThat(registry.projects("s1")).containsExactly(7L);
  }

  @Test
  void aSecondTabOfTheSameUserChangesNothing() {
    registry.subscribed("s1", "a", 7, true, 1, "Ada", t0);
    assertThat(registry.subscribed("s2", "a", 7, true, 1, "Ada", t0)).isEmpty();
    assertThat(registry.viewers(7)).hasSize(1);
    assertThat(registry.disconnected("s1")).isEmpty();
    assertThat(registry.disconnected("s2")).containsExactly(7L);
    assertThat(registry.viewers(7)).isEmpty();
    assertThat(registry.disconnected("unknown")).isEmpty();
  }

  @Test
  void viewersAreSortedByNameThenId() {
    registry.subscribed("s1", "a", 7, true, 3, "bob", t0);
    registry.subscribed("s2", "a", 7, true, 2, "Ada", t0);
    registry.subscribed("s3", "a", 7, true, 1, "Ada", t0);
    registry.subscribed("s4", "a", 8, true, 4, "Cy", t0);
    assertThat(registry.viewers(7))
        .extracting(PresenceMessage.Viewer::userId)
        .containsExactly(1L, 2L, 3L);
  }

  @Test
  void unsubscribingLeavesOnlyWhenTheLastEventsSubscriptionGoes() {
    registry.subscribed("s1", "a", 7, true, 1, "Ada", t0);
    registry.subscribed("s1", "b", 7, true, 1, "Ada", t0);
    registry.subscribed("s1", "p", 7, false, 1, "Ada", t0);
    assertThat(registry.unsubscribed("s1", "p")).isEmpty();
    assertThat(registry.unsubscribed("s1", "a")).isEmpty();
    assertThat(registry.unsubscribed("s1", "b")).containsExactly(7L);
    assertThat(registry.unsubscribed("s1", "b")).isEmpty();
    assertThat(registry.unsubscribed("nobody", "x")).isEmpty();
  }

  @Test
  void unconfirmedSessionsExpireAfterTheTtl() {
    registry.subscribed("s1", "a", 7, true, 1, "Ada", t0);
    registry.subscribed("s2", "a", 8, true, 2, "Bo", t0);
    registry.touch("s2", t0.plusSeconds(60));
    registry.touch("missing", t0.plusSeconds(60));
    assertThat(registry.expire(t0.plusSeconds(90))).isEmpty();
    assertThat(registry.expire(t0.plusSeconds(91))).isEqualTo(Set.of(7L));
    assertThat(registry.viewers(8)).hasSize(1);
    assertThat(registry.projects("s1")).isEmpty();
    assertThat(registry.subscriptions("s1")).isZero();
  }
}
