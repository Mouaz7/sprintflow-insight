package com.sprintflowinsight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.auth.UserIdentity;
import com.sprintflowinsight.auth.UserRepository;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Behaviour when PostgreSQL is slow or unreachable, with Toxiproxy between the app and the
 * database. An outage must give a clear, retryable 503 (never a 500 leaking driver text), health
 * must report it, and the app must recover by itself once the database is back.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.security.auth-rate-limit.enabled=false"})
@AutoConfigureMockMvc
class DatabaseResilienceTest {
  private static final Network NETWORK = Network.newNetwork();
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse(
                      System.getProperty("postgres.test.image", "postgres:16.10-alpine"))
                  .asCompatibleSubstituteFor("postgres"))
          .withNetwork(NETWORK)
          .withNetworkAliases("db");
  private static final ToxiproxyContainer TOXIPROXY =
      new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.12.0").withNetwork(NETWORK);
  private static final Proxy PROXY;

  static {
    POSTGRES.start();
    TOXIPROXY.start();
    try {
      PROXY =
          new ToxiproxyClient(TOXIPROXY.getHost(), TOXIPROXY.getControlPort())
              .createProxy("postgres", "0.0.0.0:8666", "db:5432");
    } catch (java.io.IOException failure) {
      throw new IllegalStateException(failure);
    }
  }

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () ->
            "jdbc:postgresql://%s:%d/%s?connectTimeout=2&socketTimeout=5"
                .formatted(
                    TOXIPROXY.getHost(),
                    TOXIPROXY.getMappedPort(8666),
                    POSTGRES.getDatabaseName()));
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.hikari.connection-timeout", () -> "2000");
    registry.add("spring.datasource.hikari.validation-timeout", () -> "1000");
    registry.add("management.endpoint.health.show-components", () -> "never");
  }

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @Autowired private TestRestTemplate rest;

  @AfterEach
  void heal() throws Exception {
    PROXY.enable();
    for (var toxic : PROXY.toxics().getAll()) toxic.remove();
  }

  @Test
  void slowDatabaseStillAnswersCorrectly() throws Exception {
    User user = user();
    PROXY.toxics().latency("slow-db", ToxicDirection.DOWNSTREAM, 250).setJitter(50);
    mvc.perform(get("/api/projects").with(as(user))).andExpect(status().isOk());
  }

  @Test
  void anExistingJdbcSessionGetsARetryableProblemDuringOutage() throws Exception {
    var csrf = rest.getForEntity("/api/auth/csrf", Map.class);
    var headers = new HttpHeaders();
    headers.set(
        HttpHeaders.COOKIE, csrf.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0]);
    headers.set((String) csrf.getBody().get("headerName"), (String) csrf.getBody().get("token"));
    var registered =
        rest.postForEntity(
            "/api/auth/register",
            new HttpEntity<>(
                Map.of(
                    "email",
                    "session-" + UUID.randomUUID() + "@example.test",
                    "password",
                    "test-password",
                    "displayName",
                    "Resilience"),
                headers),
            Map.class);
    assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.OK);
    headers = new HttpHeaders();
    headers.set(
        HttpHeaders.COOKIE,
        registered.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0]);
    var request = new HttpEntity<Void>(headers);
    assertThat(rest.exchange("/api/auth/me", HttpMethod.GET, request, Map.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    PROXY.disable();
    var unavailable = rest.exchange("/api/auth/me", HttpMethod.GET, request, String.class);
    assertThat(unavailable.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(unavailable.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
    assertThat(unavailable.getBody())
        .contains("SERVICE_UNAVAILABLE")
        .doesNotContain("jdbc", "SQLException");

    PROXY.enable();
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
    HttpStatus status;
    do {
      status =
          HttpStatus.valueOf(
              rest.exchange("/api/auth/me", HttpMethod.GET, request, Map.class)
                  .getStatusCode()
                  .value());
      if (status != HttpStatus.OK) Thread.sleep(250);
    } while (status != HttpStatus.OK && System.nanoTime() < deadline);
    assertThat(status).as("the same session works after reconnection").isEqualTo(HttpStatus.OK);
  }

  @Test
  void outageIsARetryableServiceUnavailableProblemAndRecoversWithoutRestart() throws Exception {
    User user = user();
    mvc.perform(get("/api/projects").with(as(user))).andExpect(status().isOk());

    PROXY.disable();
    mvc.perform(get("/api/projects").with(as(user)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "5"))
        .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
        .andExpect(
            jsonPath("$.detail")
                .value("The database is temporarily unavailable. Try again shortly."))
        .andExpect(
            result ->
                assertThat(result.getResponse().getContentAsString())
                    .doesNotContainIgnoringCase("jdbc")
                    .doesNotContainIgnoringCase("connection refused"));
    mvc.perform(get("/actuator/health")).andExpect(status().isServiceUnavailable());

    PROXY.enable();
    long deadline = System.currentTimeMillis() + 15_000;
    int status;
    do {
      status =
          mvc.perform(get("/api/projects").with(as(user))).andReturn().getResponse().getStatus();
      if (status != 200) Thread.sleep(250);
    } while (status != 200 && System.currentTimeMillis() < deadline);
    assertThat(status).as("recovers once the database is reachable").isEqualTo(200);
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  private User user() {
    User user = new User();
    user.setEmail("resilience-" + UUID.randomUUID() + "@example.test");
    user.setPassword("not-used");
    return users.save(user);
  }

  private RequestPostProcessor as(User user) {
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.authentication(
        new UsernamePasswordAuthenticationToken(
            new UserIdentity(user.getId(), user.getEmail()),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  }
}
