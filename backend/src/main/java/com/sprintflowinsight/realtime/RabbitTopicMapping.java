package com.sprintflowinsight.realtime;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;

/** Maps authorized project topics to RabbitMQ routing keys at the relay boundary. */
final class RabbitTopicMapping implements ExecutorChannelInterceptor {
  private static final String TOPIC_PREFIX = "/topic/";

  @Override
  public Message<?> beforeHandle(
      Message<?> message, MessageChannel channel, MessageHandler handler) {
    if (!(handler instanceof StompBrokerRelayMessageHandler)) return message;
    String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
    if (ProjectTopics.project(destination).isEmpty()) return message;
    return withDestination(
        message, TOPIC_PREFIX + destination.substring(TOPIC_PREFIX.length()).replace('/', '.'));
  }

  Message<?> fromBroker(Message<?> message) {
    String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
    if (destination == null || !destination.startsWith(TOPIC_PREFIX + "projects.")) return message;
    String original = TOPIC_PREFIX + destination.substring(TOPIC_PREFIX.length()).replace('.', '/');
    return ProjectTopics.project(original).isPresent()
        ? withDestination(message, original)
        : message;
  }

  private static Message<?> withDestination(Message<?> message, String destination) {
    var headers = StompHeaderAccessor.wrap(message);
    headers.setDestination(destination);
    // Application publications originate as SIMP MESSAGE headers. The relay assumes a STOMP
    // accessor supplied by an interceptor already has its wire command.
    if (headers.getCommand() == null) headers.updateStompCommandAsClientMessage();
    headers.setLeaveMutable(true);
    return MessageBuilder.createMessage(message.getPayload(), headers.getMessageHeaders());
  }
}
