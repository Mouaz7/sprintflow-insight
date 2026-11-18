package com.sprintflowinsight.realtime;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * Caps subscriptions per connection. A client needs two per open project; the cap stops one
 * connection from making the server run an access query per frame indefinitely.
 */
@Component
public class SubscriptionLimit implements ChannelInterceptor {
  static final int MAX_SUBSCRIPTIONS = 16;

  private final RealtimeSessions sessions;

  public SubscriptionLimit(RealtimeSessions sessions) {
    this.sessions = sessions;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    var headers = StompHeaderAccessor.wrap(message);
    if (StompCommand.SUBSCRIBE.equals(headers.getCommand())
        && sessions.subscriptions(headers.getSessionId()) >= MAX_SUBSCRIPTIONS)
      throw new MessageDeliveryException(message, "Too many subscriptions on this connection.");
    return message;
  }
}
