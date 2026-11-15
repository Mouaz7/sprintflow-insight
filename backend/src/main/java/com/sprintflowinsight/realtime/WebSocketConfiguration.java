package com.sprintflowinsight.realtime;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.task.SimpleAsyncTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/**
 * STOMP over a plain WebSocket at {@code /ws} with Spring's in-memory simple broker (one instance;
 * C6 swaps it for a RabbitMQ relay, ADR 0017). The HTTP handshake needs the signed-in session
 * cookie and a browser Origin equal to the public URL; frames are then authorised by {@link
 * RealtimeSecurityConfiguration}.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
  /** Client frames are tiny (CONNECT/SUBSCRIBE); anything larger is rejected. */
  static final int MAX_FRAME_BYTES = 16 * 1024;

  static final long HEARTBEAT_MS = 10_000;

  private final String[] allowedOrigins;
  private final RealtimeSessions sessions;
  private final SubscriptionLimit subscriptionLimit;

  public WebSocketConfiguration(
      @Value("${app.realtime.allowed-origins:${app.password-reset.base-url:http://localhost:8081}}")
          String[] allowedOrigins,
      RealtimeSessions sessions,
      SubscriptionLimit subscriptionLimit) {
    this.allowedOrigins =
        Arrays.stream(allowedOrigins)
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .map(origin -> origin.replaceAll("/+$", ""))
            .toArray(String[]::new);
    this.sessions = sessions;
    this.subscriptionLimit = subscriptionLimit;
  }

  /**
   * The application's {@code @Scheduled} scheduler. The message broker configuration declares its
   * own {@code messageBrokerTaskScheduler}, which makes Boot's scheduler auto-configuration back
   * off and would move every scheduled job onto that platform-thread pool. This restores Boot's
   * bean (virtual threads, {@code spring.task.scheduling.*}) under the name the router prefers.
   */
  @Bean(name = "taskScheduler")
  static SimpleAsyncTaskScheduler taskScheduler(SimpleAsyncTaskSchedulerBuilder builder) {
    return builder.build();
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    var httpSession = new HttpSessionHandshakeInterceptor();
    httpSession.setCopyAllAttributes(false);
    httpSession.setCopyHttpSessionId(true);
    httpSession.setCreateSession(false);
    registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins).addInterceptors(httpSession);
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    var heartbeats = new ThreadPoolTaskScheduler();
    heartbeats.setPoolSize(1);
    heartbeats.setThreadNamePrefix("stomp-heartbeat-");
    heartbeats.setDaemon(true);
    heartbeats.initialize();
    registry
        .enableSimpleBroker("/topic")
        .setHeartbeatValue(new long[] {HEARTBEAT_MS, HEARTBEAT_MS})
        .setTaskScheduler(heartbeats);
    // No @MessageMapping handlers exist; RealtimeSecurityConfiguration denies every SEND.
    registry.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    registration
        .setMessageSizeLimit(MAX_FRAME_BYTES)
        .setSendTimeLimit(10_000)
        .setSendBufferSizeLimit(512 * 1024)
        .setTimeToFirstMessage(30_000)
        .addDecoratorFactory(sessions::decorate);
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(subscriptionLimit);
  }
}
