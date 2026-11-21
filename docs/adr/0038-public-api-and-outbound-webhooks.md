# ADR 0038 — Public API and outgoing webhooks

Status accepted.

## Versioned HTTP API

`/api/v1` uses a stateless Spring Security chain for Bearer authentication. The session-only endpoints continue to use the existing security chain. The authenticated principal retains the immutable account ID and scope grants. Project authorization also checks current membership and archive state. A token has up to 90 days of validity and stores a SHA-256 digest of a random 256-bit secret. Issuance shows the value once. Deletion of the token or account revokes it. Organization MFA policies require a verified session to issue a token. A token issued without MFA loses workspace access if an organization subsequently requires MFA for the account.

The published API returns projections for projects and tasks. It uses ID keyset pagination and supports creation in the existing task workflow. Springdoc generates JSON and YAML for the versioned controllers. Public docs do not include the browser-only or management routes.

## Outgoing deliveries

One webhook configuration stores an approved event list, an HTTPS destination and an AES-GCM-encrypted signing secret. The durable Modulith listener creates one pending delivery per webhook and event. The payload contains event ID, type, project ID, task ID and event time. The worker leases one due delivery at a time, sends it outside the database transaction, and updates the result only if it still owns the lease. An interrupted worker can release a retry after its lease expires. Delivery is at least once, so the recipient should deduplicate using `X-SprintFlow-Delivery`. A retry retains its delivery UUID and event body.

Requests use JSON and include `X-SprintFlow-Delivery`, `X-SprintFlow-Event`, `X-SprintFlow-Timestamp` and `X-SprintFlow-Signature`. The timestamp is an integer Unix epoch in seconds. Compute HMAC-SHA-256 over UTF-8 bytes of the timestamp, a literal period, and the original JSON request body. Compare the lower-case hexadecimal digest, prefixed `sha256=`, in constant time. The recipient should reject stale timestamps and process a delivery UUID only once.

HTTP 2xx counts as delivered. Network failures and HTTP 408, 425, 429 or 5xx retry after 5, 30, 120 and 600 seconds, up to five total attempts. Each attempt records a status without storing the receiver body. The worker requires HTTPS, verifies DNS results against a public-address policy when connecting, rejects redirects and keeps TLS hostname verification. Archived or trashed projects do not claim new deliveries. A request already in flight can complete after archive or removal.

| Threat | Control |
| --- | --- |
| Spoofing | Random bearer secrets and owner-controlled HMAC signing secrets. Verified TLS for delivery. |
| Tampering | Timestamp and exact bytes covered by HMAC. Delivery uniqueness and lease ownership enforced in PostgreSQL. |
| Repudiation | Token last-use timestamps and delivery attempt status and timestamps. |
| Information disclosure | Scoped resource projections, one-time secret readback, digest storage and AES-GCM secret storage. |
| Denial of service | Read/write rate limit on the public API, bounded paging and queue leases with capped retries and HTTP timeouts. |
| Elevation of privilege | Current project access checks and scoped tokens. Owner/Admin webhook management and public HTTPS destination enforcement. |
