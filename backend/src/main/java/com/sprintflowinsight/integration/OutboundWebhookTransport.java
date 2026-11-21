package com.sprintflowinsight.integration;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

@Component
class OutboundWebhookTransport {
  record Result(Integer status, String result, boolean retry) {
    static Result status(int status) {
      if (status < 100 || status > 599) return new Result(null, "REJECTED", false);
      boolean delivered = status >= 200 && status < 300;
      return new Result(
          status,
          delivered ? "DELIVERED" : "REJECTED",
          status == 408 || status == 425 || status == 429 || status >= 500);
    }
  }

  private final PublicWebhookDestination destination =
      new PublicWebhookDestination(InetAddress::getAllByName);
  private final CloseableHttpClient client =
      HttpClients.custom()
          .setConnectionManager(
              PoolingHttpClientConnectionManagerBuilder.create()
                  .setDnsResolver(destination)
                  .setDefaultConnectionConfig(
                      ConnectionConfig.custom()
                          .setConnectTimeout(Timeout.ofSeconds(3))
                          .setSocketTimeout(Timeout.ofSeconds(5))
                          .build())
                  .build())
          .setDefaultRequestConfig(
              RequestConfig.custom()
                  .setConnectionRequestTimeout(Timeout.ofSeconds(2))
                  .setResponseTimeout(Timeout.ofSeconds(5))
                  .build())
          .disableRedirectHandling()
          .disableAutomaticRetries()
          .disableCookieManagement()
          .disableContentCompression()
          .build();

  void verifyDestination(URI uri) throws UnknownHostException {
    destination.resolve(uri.getHost());
  }

  Result send(URI uri, String secret, UUID id, String event, String payload) {
    try {
      verifyDestination(uri);
      String timestamp = Long.toString(Instant.now().getEpochSecond());
      var request = new HttpPost(uri);
      request.setEntity(new StringEntity(payload, ContentType.APPLICATION_JSON));
      request.setHeader("X-SprintFlow-Delivery", id.toString());
      request.setHeader("X-SprintFlow-Event", event);
      request.setHeader("X-SprintFlow-Timestamp", timestamp);
      request.setHeader("X-SprintFlow-Signature", signature(secret, timestamp, payload));
      try (var response = client.executeOpen(null, request, null)) {
        int status = response.getCode();
        if (response instanceof ModalCloseable connection) connection.close(CloseMode.IMMEDIATE);
        return Result.status(status);
      }
    } catch (PublicWebhookDestination.NonPublicAddress denied) {
      return new Result(null, "REJECTED", false);
    } catch (SocketTimeoutException timeout) {
      return new Result(null, "TIMED_OUT", true);
    } catch (IOException unavailable) {
      return new Result(null, "UNREACHABLE", true);
    }
  }

  static String signature(String secret, String timestamp, String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] signed = (timestamp + "." + payload).getBytes(StandardCharsets.UTF_8);
      return "sha256=" + HexFormat.of().formatHex(mac.doFinal(signed));
    } catch (GeneralSecurityException unavailable) {
      throw new IllegalStateException("HMAC-SHA-256 is required by the Java runtime.", unavailable);
    }
  }

  @PreDestroy
  void close() {
    client.close(CloseMode.GRACEFUL);
  }
}
