# ADR 0036 — Passkeys and audit integrity

Status: Accepted

## Passkeys

SprintFlow uses Spring Security 7’s WebAuthn relying-party operations and JDBC credential repositories. The browser creates a discoverable credential and requests user verification during both enrollment and sign-in. The server verifies the WebAuthn response against the configured origin, RP ID, challenge and user-verification policy. Successful assertion rotates the JDBC session ID and CSRF token and satisfies organization MFA requirements. Password and Google sign-in still enforce the account’s TOTP policy.

Set `SPRINTFLOW_PUBLIC_URL` to the address where users reach SprintFlow before enrolling credentials. Production requires HTTPS. `http://localhost:8081` supports local development. Changing only the port requires using the newly configured origin. Changing the hostname changes the RP ID and requires users to enroll new passkeys. Keep an independent sign-in method available during an address change.

An enrollment or deletion requires a session authenticated within ten minutes. WebAuthn enrollment associates a random user handle with the immutable account ID. The database stores the credential ID, public key, and authenticator metadata. The authenticator holds the private key. Account deletion removes the account’s credentials. Up to 20 credentials can be registered per account.

Each challenge is valid for two minutes and bound to a session and purpose. The challenge digest claim is atomic and commits even if the subsequent verification fails. A retry starts with fresh options. Browser requests obtain the session CSRF token. Admission limits apply at the options and completion endpoints.

## Administrative audit

PostgreSQL serializes append operations on the head row for each project and for the separate account stream. Each event links to the previous hash, beginning with 32 zero bytes. SHA-256 covers the previous hash followed by the versioned UTF-8 representation of a PostgreSQL JSONB array of the event’s persisted fields. The sequence position is part of that payload. The update to the head and the insert commit with the original administrative change. V34 preserves the fields of existing events and backfills their proof metadata in stored ID order.

The daily job at 00:05 UTC verifies the chain and captures at most one authenticated head per stream per day. The existing AES-256-GCM keyring binds the stream, day, position and head hash inside the sealed value. Key rotation re-encrypts anchors with other registered encrypted columns. The per-project verifier checks the retained suffix, stored head and most recent available daily proof in one repeatable-read snapshot.

Retention advances the floor to the last verified event in the expired prefix before deleting those rows. The floor lets later events remain verifiable across the 90-day deletion boundary. An event beyond a broken link or a newer predecessor waits for verification or expiration of that predecessor. Original details stay subject to the existing retention policy and authorization.

The application key protects an anchor against a database-only rewrite while its sealed proof is available. An operator with full database control can remove anchors or restore the entire database to an earlier state. A separately retained checkpoint is required to distinguish such a rollback from the earlier valid state. The API reports whether an anchor is available and its day, position and hash. The verification endpoint is project-scoped and the account stream has no public verification route.

## Access and threats

| Operation | Anonymous | Project Viewer or Member | Project Admin or Owner |
| --- | --- | --- | --- |
| Passkey configuration and sign-in | Allowed | Allowed | Allowed |
| Read own passkey metadata | Denied | Allowed | Allowed |
| Enroll or delete own passkey | Denied | Recent sign-in required | Recent sign-in required |
| Access another account’s passkey | Denied | Denied | Denied |
| Verify a project’s audit chain | Denied | Denied | Allowed, including archived projects |

| STRIDE threat | Surface and control | Evidence |
| --- | --- | --- |
| Spoofing | WebAuthn checks the RP ID, origin and verified authenticator response. The server consumes a session-bound challenge and rotates session identity after sign-in. | `passkeys.e2e.ts`, `PasskeyHttpIntegrationTest` |
| Tampering | Audit rows and anchors are verified together. A broken stream does not receive a new daily anchor. | `AuditChainHashTest`, `AuditIntegrityIntegrationTest` |
| Repudiation | Administrative mutations record actor, role and target metadata in the same transaction. A daily anchor authenticates the head position as observed by SprintFlow. | `AdminAuditIntegrationTest`, `AuditIntegrityIntegrationTest` |
| Information disclosure | Account access gates credential metadata. Project roles gate audit verification. Challenge responses are served with `Cache-Control: no-store`. | `PasskeyHttpIntegrationTest`, `AuditIntegrityIntegrationTest` |
| Denial of service | Challenge creation and completion are rate-limited. Responses have bounded parsing and expired challenges are cleaned in batches. Audit verification streams a project’s rows and the retention job caps each deletion batch. | `RateLimitPolicy`, `PasskeyChallenges`, `AuditIntegrityService`, `AdminAuditIntegrationTest` |
| Elevation of privilege | Enrollment and deletion require recent authentication and credential ownership. Project `MANAGE_ARCHIVED` access controls audit verification. | `PasskeyHttpIntegrationTest`, `AuditIntegrityIntegrationTest` |

See the [Spring Security passkey reference](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/passkeys.html) and [ASVS assessment](../security/asvs.md).
