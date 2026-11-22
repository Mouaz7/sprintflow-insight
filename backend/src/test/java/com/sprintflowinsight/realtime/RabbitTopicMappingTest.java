package com.sprintflowinsight.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

class RabbitTopicMappingTest {
  private final RabbitTopicMapping mapping = new RabbitTopicMapping();
  private final MessageChannel channel = mock(MessageChannel.class);
  private final MessageHandler relay = mock(StompBrokerRelayMessageHandler.class);

  @Test
  void mapsOnlyTheRelayCopyAndRestoresTheClientDestination() {
    String topic = ProjectTopics.collaboration(42, "TASK", 7, "updates");
    var original = message(StompCommand.SUBSCRIBE, topic);
    assertThat(mapping.beforeHandle(original, channel, mock(MessageHandler.class)))
        .isSameAs(original);
    var relayCopy = mapping.beforeHandle(original, channel, relay);
    var accessor = StompHeaderAccessor.wrap(relayCopy);
    assertThat(accessor.getCommand()).isEqualTo(StompCommand.SUBSCRIBE);
    assertThat(accessor.getDestination())
        .isEqualTo("/topic/projects.42.collaboration.TASK.7.updates");
    assertThat(accessor.getFirstNativeHeader(StompHeaderAccessor.STOMP_DESTINATION_HEADER))
        .isEqualTo(accessor.getDestination());
    assertThat(StompHeaderAccessor.wrap(original).getDestination()).isEqualTo(topic);
    var returned = mapping.fromBroker(message(StompCommand.MESSAGE, accessor.getDestination()));
    assertThat(StompHeaderAccessor.wrap(returned).getDestination()).isEqualTo(topic);
  }

  @Test
  void doesNotTranslateNonProjectOrWildcardDestinations() {
    for (var destination :
        java.util.List.of("/topic/projects.*", "/app/projects/1/presence", "/topic/projects/1/#")) {
      var original = message(StompCommand.SUBSCRIBE, destination);
      assertThat(mapping.beforeHandle(original, channel, relay)).isSameAs(original);
      assertThat(mapping.fromBroker(original)).isSameAs(original);
    }
  }

  @Test
  void addsTheSendCommandToApplicationPublications() {
    var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
    headers.setDestination(ProjectTopics.events(42));
    var original = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    var translated = StompHeaderAccessor.wrap(mapping.beforeHandle(original, channel, relay));
    assertThat(translated.getCommand()).isEqualTo(StompCommand.SEND);
    assertThat(translated.getDestination()).isEqualTo("/topic/projects.42");
  }

  private static Message<byte[]> message(StompCommand command, String destination) {
    var headers = StompHeaderAccessor.create(command);
    headers.setDestination(destination);
    return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
  }
}
