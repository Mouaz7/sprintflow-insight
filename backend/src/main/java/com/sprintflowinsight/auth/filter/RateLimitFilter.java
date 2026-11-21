package com.sprintflowinsight.auth.filter;

import com.sprintflowinsight.auth.SecurityConfiguration;
import com.sprintflowinsight.auth.UserIdentity;
import com.sprintflowinsight.common.error.ErrorCode;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Applies {@link RateLimitPolicy} to API requests and answers excess with an RFC 9457 problem,
 * {@code Retry-After} and {@code RateLimit-*} headers.
 *
 * <p>Runs right after the Spring Security chain so the authenticated account is known. Client
 * addresses come from the connection, never from forwarded headers, unless a trusted proxy is
 * configured at the server level.
 */
@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1)
public class RateLimitFilter extends OncePerRequestFilter {
  private static final Logger SECURITY_LOG = LoggerFactory.getLogger("security.events");
  private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH", "DELETE");
  private static final Map<String, RateLimitPolicy> CREDENTIAL_ENDPOINTS =
      Map.of(
          "/api/auth/login", RateLimitPolicy.LOGIN,
          "/api/auth/google", RateLimitPolicy.GOOGLE_LOGIN,
          "/api/auth/two-factor/verify", RateLimitPolicy.MFA_VERIFY,
          "/api/auth/passkeys/login/options", RateLimitPolicy.PASSKEY_LOGIN,
          "/api/auth/passkeys/login/complete", RateLimitPolicy.PASSKEY_LOGIN,
          "/api/auth/register", RateLimitPolicy.REGISTER,
          "/api/auth/password/reset/request", RateLimitPolicy.RESET_REQUEST,
          "/api/auth/password/reset/recovery", RateLimitPolicy.RESET_RECOVERY,
          "/api/auth/password/reset/confirm", RateLimitPolicy.RESET_CONFIRM);

  private final boolean enabled;
  private final RateLimiter limiter;
  private final ObjectMapper json;

  @org.springframework.beans.factory.annotation.Autowired
  public RateLimitFilter(
      @Value("${app.security.rate-limit.enabled:${app.security.auth-rate-limit.enabled:true}}")
          boolean enabled,
      ObjectMapper json) {
    this(enabled, new RateLimiter(TimeMeter.SYSTEM_NANOTIME), json);
  }

  public RateLimitFilter(boolean enabled, RateLimiter limiter, ObjectMapper json) {
    this.enabled = enabled;
    this.limiter = limiter;
    this.json = json;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Optional<RateLimitPolicy> policy = enabled ? policyFor(request) : Optional.empty();
    if (policy.isEmpty()) {
      chain.doFilter(request, response);
      return;
    }
    RateLimitPolicy applied = policy.get();
    ConsumptionProbe probe = limiter.tryConsume(applied, subject(applied, request));
    response.setHeader("RateLimit-Limit", Long.toString(applied.capacity()));
    response.setHeader(
        "RateLimit-Remaining", Long.toString(Math.max(0, probe.getRemainingTokens())));
    if (probe.isConsumed()) {
      chain.doFilter(request, response);
      return;
    }
    long retrySeconds =
        Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
    SECURITY_LOG.warn("Rate limit {} reached.", applied);
    response.setHeader("Retry-After", Long.toString(retrySeconds));
    SecurityConfiguration.writeProblem(
        json,
        request,
        response,
        ErrorCode.RATE_LIMITED,
        "Too many requests. Wait a moment, then try again.");
  }

  static Optional<RateLimitPolicy> policyFor(HttpServletRequest request) {
    String path = request.getServletPath();
    if (path == null || path.isEmpty())
      path = request.getRequestURI().substring(request.getContextPath().length());
    if (path.startsWith("/api/v1/projects")) return Optional.of(RateLimitPolicy.PUBLIC_API);
    if (!WRITES.contains(request.getMethod()) || !path.startsWith("/api/")) return Optional.empty();
    RateLimitPolicy credential = CREDENTIAL_ENDPOINTS.get(path);
    if (credential != null) return Optional.of(credential);
    if (path.startsWith("/api/auth/logout")) return Optional.empty();
    if (path.equals("/api/account/export")) return Optional.of(RateLimitPolicy.EXPORT);
    if (path.endsWith("/attachments/upload")) return Optional.of(RateLimitPolicy.UPLOAD);
    return Optional.of(RateLimitPolicy.WRITE);
  }

  private static String subject(RateLimitPolicy policy, HttpServletRequest request) {
    if (policy.scope() == RateLimitPolicy.Scope.ACCOUNT) {
      var authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication != null && authentication.getPrincipal() instanceof UserIdentity identity)
        return "user:" + identity.userId();
    }
    return "ip:" + request.getRemoteAddr();
  }

  @Scheduled(fixedDelay = 60_000)
  public void evictIdleBuckets() {
    limiter.evictIdle();
  }
}
