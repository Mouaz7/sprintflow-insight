package com.sprintflowinsight.properties;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.common.ratelimit.RateLimitPolicy;
import com.sprintflowinsight.common.ratelimit.RateLimiter;
import io.github.bucket4j.TimeMeter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

/**
 * Safety of the credential limiter for arbitrary request timings: within any one refill window no
 * client gets more attempts than the policy allows.
 */
class RateLimiterProperties {
  @Property(tries = 300)
  void loginNeverAllowsMoreThanThirtyAttemptsPerMinute(
      @ForAll @Size(min = 1, max = 200) List<@IntRange(min = 0, max = 5_000) Integer> gapsMillis) {
    AtomicLong nanos = new AtomicLong();
    TimeMeter clock =
        new TimeMeter() {
          public long currentTimeNanos() {
            return nanos.get();
          }

          public boolean isWallClockBased() {
            return false;
          }
        };
    RateLimiter limiter = new RateLimiter(clock);
    // Establish the refill origin at time zero, independently of the generated gaps.
    assertThat(limiter.tryConsume(RateLimitPolicy.LOGIN, "ip:198.51.100.7").isConsumed()).isTrue();
    long windowStart = 0;
    int allowedInWindow = 1;
    for (int gap : gapsMillis) {
      nanos.addAndGet(gap * 1_000_000L);
      long window = nanos.get() / 60_000_000_000L;
      if (window != windowStart) {
        windowStart = window;
        allowedInWindow = 0;
      }
      boolean allowed = limiter.tryConsume(RateLimitPolicy.LOGIN, "ip:198.51.100.7").isConsumed();
      assertThat(allowed).isEqualTo(allowedInWindow < 30);
      if (allowed) allowedInWindow++;
      assertThat(allowedInWindow).isLessThanOrEqualTo(30);
    }
  }
}
