package com.sprintflowinsight.realtime;

import java.util.Arrays;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.task.SimpleAsyncTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/**
 * STOMP over a WebSocket at {@code /ws}, with a RabbitMQ broker relay in the multi-instance
 * deployment. The HTTP handshake needs the signed-in session cookie and a browser Origin equal to
 * the public URL; frames are then authorised by {@link RealtimeSecurityConfiguration}.
 */
@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(RelaySettings.class)
public class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
  /** Client frames are tiny (CONNECT/SUBSCRIBE); anything larger is rejected. */
  static final int MAX_FRAME_BYTES = 16 * 1024;

  static final long HEARTBEAT_MS = 10_000;

  private final String[] allowedOrigins;
  private final RealtimeSessions sessions;
  private final SubscriptionLimit subscriptionLimit;
  private final RelaySettings relay;
  private final String diagnosticsInstance;
  private final RabbitTopicMapping rabbitTopics = new RabbitTopicMapping();

  public WebSocketConfiguration(
      @Value("${app.realtime.allowed-origins:${app.password-reset.base-url:http://localhost:8081}}")
          String[] allowedOrigins,
      RealtimeSessions sessions,
      SubscriptionLimit subscriptionLimit,
      RelaySettings relay,
      @Value("${app.realtime.diagnostics-instance:}") String diagnosticsInstance) {
    this.allowedOrigins =
        Arrays.stream(allowedOrigins)
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .map(origin -> origin.replaceAll("/+$", ""))
            .toArray(String[]::new);
    this.sessions = sessions;
    this.subscriptionLimit = subscriptionLimit;
    this.relay = relay;
    this.diagnosticsInstance = diagnosticsInstance;
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

  /**
   * Broker executor beans make Boot back off from application async execution as well. Keep
   * listeners bounded on virtual threads, but queue nested durable publications instead of blocking
   * all four listener threads while each tries to submit its follow-up event.
   */
  @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
  static ThreadPoolTaskExecutor taskExecutor(
      @Value("${spring.task.execution.simple.concurrency-limit:4}") int concurrency,
      @Value("${app.events.executor-queue-capacity:1000}") int queueCapacity) {
    if (concurrency < 1 || queueCapacity < 1)
      throw new IllegalArgumentException("Event concurrency and queue capacity must be positive.");
    var executor = new ThreadPoolTaskExecutor();
    executor.setVirtualThreads(true);
    executor.setThreadNamePrefix("module-events-");
    executor.setCorePoolSize(concurrency);
    executor.setMaxPoolSize(concurrency);
    executor.setQueueCapacity(queueCapacity);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(10);
    return executor;
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    // Inbound frames run on their WebSocket session's caller thread below. Spring 7.0's
    // OrderedMessageChannelDecorator catches authorization failures and returns true, which
    // otherwise suppresses ERROR/close and publishes a subscribe event for a denied frame.
    registry.setPreserveReceiveOrder(false);
    var httpSession = new HttpSessionHandshakeInterceptor();
    httpSession.setCopyAllAttributes(false);
    httpSession.setCopyHttpSessionId(true);
    httpSession.setCreateSession(false);
    var endpoint =
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins).addInterceptors(httpSession);
    if (!diagnosticsInstance.isBlank()) {
      endpoint.addInterceptors(
          new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler handler,
                Map<String, Object> attributes) {
              response.getHeaders().set("X-SprintFlow-Instance", diagnosticsInstance);
              return true;
            }

            @Override
            public void afterHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler handler,
                Exception exception) {}
          });
    }
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.setPreservePublishOrder(true);
    // Client SEND destinations are narrowed by RealtimeSecurityConfiguration.
    registry.setApplicationDestinationPrefixes("/app");
    if (relay.enabled()) {
      registry.configureBrokerChannel().interceptors(rabbitTopics);
      registry
          .enableStompBrokerRelay("/topic")
          .setRelayHost(relay.host())
          .setRelayPort(relay.port())
          .setVirtualHost(relay.virtualHost())
          .setClientLogin(relay.login())
          .setClientPasscode(relay.passcode())
          .setSystemLogin(relay.login())
          .setSystemPasscode(relay.passcode())
          .setSystemHeartbeatSendInterval(HEARTBEAT_MS)
          .setSystemHeartbeatReceiveInterval(HEARTBEAT_MS);
      return;
    }
    var heartbeats = new ThreadPoolTaskScheduler();
    heartbeats.setPoolSize(1);
    heartbeats.setThreadNamePrefix("stomp-heartbeat-");
    heartbeats.setDaemon(true);
    heartbeats.initialize();
    registry
        .enableSimpleBroker("/topic")
        .setHeartbeatValue(new long[] {HEARTBEAT_MS, HEARTBEAT_MS})
        .setTaskScheduler(heartbeats);
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
    // Preserve each socket's receive order while allowing failures to reach the protocol
    // handler. Different sockets retain their own transport threads; domain writes use REST.
    registration.executor(new SyncTaskExecutor());
    registration.interceptors(subscriptionLimit);
    if (relay.enabled()) registration.interceptors(rabbitTopics);
  }

  @Override
  public void configureClientOutboundChannel(ChannelRegistration registration) {
    if (relay.enabled())
      registration.interceptors(
          new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
              return rabbitTopics.fromBroker(message);
            }
          });
  }
}
