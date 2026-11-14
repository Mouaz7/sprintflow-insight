package com.sprintflowinsight.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTest {
  @Test
  void limitsAttemptsIgnoresSpoofedHeadersAndAllowsRetryAfterExpiry() throws Exception {
    AtomicLong now = new AtomicLong(0);
    Clock clock =
        new Clock() {
          public ZoneId getZone() {
            return ZoneOffset.UTC;
          }

          public Clock withZone(ZoneId zone) {
            return this;
          }

          public Instant instant() {
            return Instant.ofEpochMilli(now.get());
          }
        };
    var filter = new AuthRateLimitFilter(true, clock);
    for (int attempt = 0; attempt < 30; attempt++) {
      assertThat(request(filter, "/api/auth/login", "127.0.0.1").getStatus()).isEqualTo(200);
    }
    var blocked = request(filter, "/api/auth/login", "127.0.0.1");
    assertThat(blocked.getStatus()).isEqualTo(429);
    assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
    assertThat(request(filter, "/api/auth/login", "127.0.0.2").getStatus()).isEqualTo(200);
    assertThat(request(filter, "/api/auth/csrf", "127.0.0.1").getStatus()).isEqualTo(200);
    now.set(60_000);
    assertThat(request(filter, "/api/auth/login", "127.0.0.1").getStatus()).isEqualTo(200);
  }

  @Test
  void appliesLowerLimitToRecoveryRequests() throws Exception {
    var filter = new AuthRateLimitFilter(true);
    for (int attempt = 0; attempt < 10; attempt++) {
      assertThat(request(filter, "/api/auth/password/reset/request", "127.0.0.1").getStatus())
          .isEqualTo(200);
    }
    assertThat(request(filter, "/api/auth/password/reset/request", "127.0.0.1").getStatus())
        .isEqualTo(429);
  }

  private MockHttpServletResponse request(AuthRateLimitFilter filter, String path, String address)
      throws Exception {
    var request = new MockHttpServletRequest("POST", path);
    request.setServletPath(path);
    request.setRemoteAddr(address);
    request.addHeader("X-Forwarded-For", java.util.UUID.randomUUID().toString());
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, (req, res) -> {});
    return response;
  }
}
