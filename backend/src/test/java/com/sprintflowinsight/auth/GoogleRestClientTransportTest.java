package com.sprintflowinsight.auth;

import static org.assertj.core.api.Assertions.*;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponseException;
import com.sprintflowinsight.common.HttpClientConfiguration;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GoogleRestClientTransportTest {
  /**
   * Generous limits for every request that is expected to succeed or fail for another reason, so
   * a loaded CI runner cannot turn them into spurious timeouts. Only the read-timeout test uses a
   * short limit on purpose.
   */
  private static final Duration GENEROUS = Duration.ofSeconds(20);

  private HttpServer server;
  private String base;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    server.start();
    base = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private GoogleRestClientTransport transport(Duration read) {
    var client =
        new HttpClientConfiguration().outboundRestClient(RestClient.builder(), GENEROUS, read);
    return new GoogleRestClientTransport(client);
  }

  @Test
  void preservesSigningKeyBodyAndCacheHeaders() throws Exception {
    server.createContext(
        "/keys",
        exchange -> {
          assertThat(exchange.getRequestHeaders().getFirst("X-Verification")).isEqualTo("fixture");
          byte[] body = "{\"keys\":[]}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.getResponseHeaders().add("Cache-Control", "public,max-age=3600");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    var request =
        transport(GENEROUS)
            .createRequestFactory()
            .buildGetRequest(new GenericUrl(base + "/keys"));
    request.getHeaders().set("X-Verification", "fixture");
    var response = request.execute();
    try {
      assertThat(response.getStatusCode()).isEqualTo(200);
      assertThat(response.getContentType()).isEqualTo("application/json");
      assertThat(response.getHeaders().getCacheControl()).isEqualTo("public,max-age=3600");
      assertThat(response.parseAsString()).isEqualTo("{\"keys\":[]}");
    } finally {
      response.disconnect();
    }
  }

  @Test
  void rejectsUnexpectedMethodsAndOversizedKeyDocuments() throws Exception {
    assertThatThrownBy(
            () ->
                transport(GENEROUS)
                    .createRequestFactory()
                    .buildPostRequest(new GenericUrl(base), null)
                    .execute())
        .isInstanceOf(java.io.IOException.class);
    server.createContext(
        "/large",
        exchange -> {
          byte[] body = new byte[1_048_577];
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    assertThatThrownBy(
            () ->
                transport(GENEROUS)
                    .createRequestFactory()
                    .buildGetRequest(new GenericUrl(base + "/large"))
                    .execute())
        .isInstanceOf(java.io.IOException.class);
  }

  @Test
  void enforcesReadTimeoutAndPreservesNonSuccessfulStatus() throws Exception {
    server.createContext(
        "/slow",
        exchange -> {
          try {
            Thread.sleep(1000);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    assertThatThrownBy(
            () ->
                transport(Duration.ofMillis(100))
                    .createRequestFactory()
                    .buildGetRequest(new GenericUrl(base + "/slow"))
                    .execute())
        .isInstanceOf(java.io.IOException.class);
    server.createContext(
        "/unavailable",
        exchange -> {
          exchange.sendResponseHeaders(503, -1);
          exchange.close();
        });
    assertThatThrownBy(
            () ->
                transport(GENEROUS)
                    .createRequestFactory()
                    .buildGetRequest(new GenericUrl(base + "/unavailable"))
                    .execute())
        .isInstanceOfSatisfying(
            HttpResponseException.class, error -> assertThat(error.getStatusCode()).isEqualTo(503));
  }
}
