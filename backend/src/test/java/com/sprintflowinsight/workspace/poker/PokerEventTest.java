package com.sprintflowinsight.workspace.poker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sprintflowinsight.realtime.RealtimeEventListener;
import com.sprintflowinsight.realtime.RealtimePublisher;
import com.sprintflowinsight.workspace.events.PokerSessionChanged;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PokerEventTest {
  @Test
  void durableAndRealtimeEventsContainOnlyInvalidationMetadata() {
    var event = new PokerSessionChanged(UUID.randomUUID(), 7L, 2L, Instant.now(), 11L, 3L);
    var json = JsonMapper.builder().build().valueToTree(event);
    assertThat(json.propertyNames())
        .containsExactlyInAnyOrder(
            "eventId", "projectId", "actorUserId", "occurredAt", "sessionId", "version");
    var publisher = mock(RealtimePublisher.class);
    new RealtimeEventListener(publisher).pokerChanged(event);
    verify(publisher)
        .publish(event.eventId(), 7L, "PokerSessionChanged", 11L, 3L, event.occurredAt());
    verifyNoMoreInteractions(publisher);
  }
}
