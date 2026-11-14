# 0003 – Token-bucket rate limiting per address and per account

**Status:** Accepted (phase A1)

## Context
Only six credential routes were limited, with a hand-written fixed window per IP. A signed-in account could create or edit data without limit, from any number of addresses.

## Decision
- Bucket4j token buckets in `RateLimitFilter`, which runs right after Spring Security so the account is known.
- Credential routes: per client address, with a fixed refill each minute (30 login/Google/token confirmation, 20 registration, 10 recovery). A fixed refill stops an attacker from spreading guesses across a smooth refill.
- All other API writes: per account, 300 per minute with greedy (smooth) refill. Uploads: 30 per minute.
- Responses carry `RateLimit-Limit` and `RateLimit-Remaining`. A rejection is a `RATE_LIMITED` problem with `Retry-After`.
- Buckets are bounded (50 000); idle buckets are evicted after ten minutes. When the map is full, new subjects are refused instead of growing memory.
- Forwarded headers are never trusted by the filter.

## Consequences
- Limits are per instance. For several instances (C6), the same policies move to a shared store (a Bucket4j JDBC `ProxyManager` on PostgreSQL) without changing the filter's contract.
- The time source is injectable, so tests are deterministic.
