package com.sprintflowinsight.common.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token buckets keyed by policy and subject. Bounded: idle buckets are dropped by
 * {@link #evictIdle()}, and when the map is full new subjects are refused rather than letting an
 * attacker exhaust memory with random keys.
 */
public class RateLimiter {
  static final int MAX_BUCKETS = 50_000;
  private static final long IDLE_NANOS = 10L * 60 * 1_000_000_000L;
  private static final long ONE_MINUTE = 60L * 1_000_000_000L;

  private final TimeMeter time;
  private final Map<String, Entry> buckets = new ConcurrentHashMap<>();

  private record Entry(Bucket bucket, long[] lastUsed) {}

  public RateLimiter(TimeMeter time) {
    this.time = time;
  }

  /** Consumes one token. A refused probe carries the wait until the next token. */
  public ConsumptionProbe tryConsume(RateLimitPolicy policy, String subject) {
    String key = policy.name() + ':' + subject;
    Entry entry = buckets.get(key);
    if (entry == null) {
      if (buckets.size() >= MAX_BUCKETS) evictIdle();
      if (buckets.size() >= MAX_BUCKETS) return ConsumptionProbe.rejected(0, ONE_MINUTE, ONE_MINUTE);
      entry =
          buckets.computeIfAbsent(
              key,
              ignored ->
                  new Entry(
                      Bucket.builder()
                          .addLimit(policy.bandwidth())
                          .withCustomTimePrecision(time)
                          .build(),
                      new long[] {time.currentTimeNanos()}));
    }
    entry.lastUsed()[0] = time.currentTimeNanos();
    return entry.bucket().tryConsumeAndReturnRemaining(1);
  }

  public void evictIdle() {
    long now = time.currentTimeNanos();
    buckets.values().removeIf(entry -> now - entry.lastUsed()[0] > IDLE_NANOS);
  }

  int size() {
    return buckets.size();
  }
}
