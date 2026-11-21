package com.sprintflowinsight.auth.filter;

import io.github.bucket4j.Bandwidth;
import java.time.Duration;

/**
 * Who is limited, how much, and how tokens come back.
 *
 * <p>Credential endpoints use a fixed refill per client address (an attacker cannot spread guesses
 * across a smooth refill). Ordinary writes are limited per account with a greedy refill, which
 * allows short bursts but caps sustained automated use.
 */
public enum RateLimitPolicy {
  LOGIN(Scope.CLIENT_ADDRESS, intervally(30)),
  GOOGLE_LOGIN(Scope.CLIENT_ADDRESS, intervally(30)),
  MFA_VERIFY(Scope.CLIENT_ADDRESS, intervally(20)),
  PASSKEY_LOGIN(Scope.CLIENT_ADDRESS, intervally(30)),
  REGISTER(Scope.CLIENT_ADDRESS, intervally(20)),
  RESET_REQUEST(Scope.CLIENT_ADDRESS, intervally(10)),
  RESET_RECOVERY(Scope.CLIENT_ADDRESS, intervally(10)),
  RESET_CONFIRM(Scope.CLIENT_ADDRESS, intervally(30)),
  PUBLIC_API(Scope.ACCOUNT, greedy(120)),
  EXPORT(Scope.ACCOUNT, greedy(2)),
  UPLOAD(Scope.ACCOUNT, greedy(30)),
  WRITE(Scope.ACCOUNT, greedy(300));

  enum Scope {
    CLIENT_ADDRESS,
    ACCOUNT
  }

  private final Scope scope;
  private final Bandwidth bandwidth;

  RateLimitPolicy(Scope scope, Bandwidth bandwidth) {
    this.scope = scope;
    this.bandwidth = bandwidth;
  }

  Scope scope() {
    return scope;
  }

  Bandwidth bandwidth() {
    return bandwidth;
  }

  long capacity() {
    return bandwidth.getCapacity();
  }

  private static Bandwidth intervally(int perMinute) {
    return Bandwidth.builder()
        .capacity(perMinute)
        .refillIntervally(perMinute, Duration.ofMinutes(1))
        .build();
  }

  private static Bandwidth greedy(int perMinute) {
    return Bandwidth.builder()
        .capacity(perMinute)
        .refillGreedy(perMinute, Duration.ofMinutes(1))
        .build();
  }
}
