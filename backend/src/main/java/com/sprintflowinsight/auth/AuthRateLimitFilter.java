package com.sprintflowinsight.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthRateLimitFilter extends OncePerRequestFilter {
  private static final long WINDOW_MILLIS = 60_000;
  private static final int MAX_BUCKETS = 10_000;
  private static final Map<String, Integer> LIMITS =
      Map.of(
          "/api/auth/login", 30,
          "/api/auth/google", 30,
          "/api/auth/register", 20,
          "/api/auth/password/reset/request", 10,
          "/api/auth/password/reset/recovery", 10,
          "/api/auth/password/reset/confirm", 30);

  private final Map<String, Window> windows = new HashMap<>();
  private final boolean enabled;
  private final Clock clock;

  @org.springframework.beans.factory.annotation.Autowired
  public AuthRateLimitFilter(
      @Value("${app.security.auth-rate-limit.enabled:true}") boolean enabled) {
    this(enabled, Clock.systemUTC());
  }

  AuthRateLimitFilter(boolean enabled, Clock clock) {
    this.enabled = enabled;
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getServletPath();
    if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
    Integer limit = LIMITS.get(path);
    if (!enabled || !"POST".equals(request.getMethod()) || limit == null) {
      chain.doFilter(request, response);
      return;
    }

    // Forwarded headers are untrusted unless a proxy is explicitly configured.
    String key = request.getRemoteAddr() + ":" + path;
    long retrySeconds = consume(key, limit);
    if (retrySeconds == 0) {
      chain.doFilter(request, response);
      return;
    }
    response.setStatus(429);
    response.setHeader("Retry-After", Long.toString(retrySeconds));
    response.setContentType("application/json");
    response
        .getWriter()
        .write("{\"message\":\"Too many requests. Wait a moment, then try again.\"}");
  }

  private synchronized long consume(String key, int limit) {
    long now = clock.millis();
    Window window = windows.get(key);
    if (window == null || window.expiresAt() <= now) {
      if (windows.size() >= MAX_BUCKETS) removeExpiredWindows();
      if (!windows.containsKey(key) && windows.size() >= MAX_BUCKETS) return 60;
      windows.put(key, new Window(1, now + WINDOW_MILLIS));
      return 0;
    }
    if (window.requests() >= limit) {
      return Math.max(1, (window.expiresAt() - now + 999) / 1000);
    }
    windows.put(key, new Window(window.requests() + 1, window.expiresAt()));
    return 0;
  }

  @Scheduled(fixedDelay = WINDOW_MILLIS)
  public synchronized void removeExpiredWindows() {
    long now = clock.millis();
    windows.values().removeIf(window -> window.expiresAt() <= now);
  }

  private record Window(int requests, long expiresAt) {}
}
