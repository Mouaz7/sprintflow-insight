package com.sprintflowinsight.auth.filter;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import com.sprintflowinsight.auth.UserIdentity;
import com.sprintflowinsight.auth.filter.RateLimitFilter;
import io.github.bucket4j.TimeMeter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RateLimitFilterTest {
  private final AtomicLong nanos = new AtomicLong();
  private final TimeMeter clock =
      new TimeMeter() {
        public long currentTimeNanos() {
          return nanos.get();
        }

        public boolean isWallClockBased() {
          return false;
        }
      };
  private final RateLimiter limiter = new RateLimiter(clock);
  private final RateLimitFilter filter = new RateLimitFilter(true, limiter, new ObjectMapper());

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void credentialEndpointsAreLimitedPerAddressAndIgnoreSpoofedHeaders() throws Exception {
    for (int attempt = 0; attempt < 30; attempt++)
      assertThat(post("/api/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
    MockHttpServletResponse blocked = post("/api/auth/login", "10.0.0.1");
    assertThat(blocked.getStatus()).isEqualTo(429);
    assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
    assertThat(blocked.getContentType()).isEqualTo("application/problem+json");
    assertThat(blocked.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"");
    assertThat(post("/api/auth/login", "10.0.0.2").getStatus()).isEqualTo(200);

    nanos.addAndGet(60_000_000_000L);
    assertThat(post("/api/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
  }

  @Test
  void recoveryHasALowerLimitThanLogin() throws Exception {
    for (int attempt = 0; attempt < 10; attempt++)
      assertThat(post("/api/auth/password/reset/request", "10.0.0.3").getStatus()).isEqualTo(200);
    assertThat(post("/api/auth/password/reset/request", "10.0.0.3").getStatus()).isEqualTo(429);
  }

  @Test
  void writesAreLimitedPerAccountRegardlessOfAddressAndRefillSmoothly() throws Exception {
    signIn(7L);
    for (int i = 0; i < 300; i++)
      assertThat(post("/api/tasks", "10.0.0." + (i % 200)).getStatus()).isEqualTo(200);
    MockHttpServletResponse blocked = post("/api/tasks", "10.9.9.9");
    assertThat(blocked.getStatus()).isEqualTo(429);
    assertThat(blocked.getHeader("RateLimit-Remaining")).isEqualTo("0");

    nanos.addAndGet(1_000_000_000L); // greedy refill: 300/min = 5 tokens per second
    for (int i = 0; i < 5; i++)
      assertThat(post("/api/tasks", "10.9.9.9").getStatus()).isEqualTo(200);
    assertThat(post("/api/tasks", "10.9.9.9").getStatus()).isEqualTo(429);

    signIn(8L);
    assertThat(post("/api/tasks", "10.9.9.9").getStatus())
        .as("other accounts unaffected")
        .isEqualTo(200);
  }

  @Test
  void readsAndNonApiRequestsAreNeverLimited() {
    var get = new MockHttpServletRequest("GET", "/api/tasks");
    get.setServletPath("/api/tasks");
    assertThat(RateLimitFilter.policyFor(get)).isEmpty();
    var page = new MockHttpServletRequest("POST", "/");
    page.setServletPath("/");
    assertThat(RateLimitFilter.policyFor(page)).isEmpty();
    var upload = new MockHttpServletRequest("POST", "/api/tasks/1/attachments/upload");
    upload.setServletPath("/api/tasks/1/attachments/upload");
    assertThat(RateLimitFilter.policyFor(upload)).contains(RateLimitPolicy.UPLOAD);
  }

  @Test
  void idleBucketsAreEvicted() throws Exception {
    post("/api/auth/login", "10.1.1.1");
    assertThat(limiter.size()).isEqualTo(1);
    nanos.addAndGet(11L * 60 * 1_000_000_000L);
    limiter.evictIdle();
    assertThat(limiter.size()).isZero();
  }

  private void signIn(long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new UserIdentity(userId, "u" + userId + "@example.test"), null, List.of()));
  }

  private MockHttpServletResponse post(String path, String address) throws Exception {
    var request = new MockHttpServletRequest("POST", path);
    request.setServletPath(path);
    request.setRemoteAddr(address);
    request.addHeader("X-Forwarded-For", "203.0.113." + nanos.get() % 250);
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }
}
