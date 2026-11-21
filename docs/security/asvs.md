# OWASP ASVS 5.0 level 2 assessment

The [control matrix](asvs-5-l2.csv) covers all 253 requirements applicable to an ASVS level 2 assessment, including the 70 level 1 requirements and 183 level 2 requirements. Each row identifies an official requirement, assessment, evidence paths and a finding. Look up each ID in the [OWASP ASVS 5.0.0 release](https://github.com/OWASP/ASVS/releases/tag/v5.0.0_release).

| Assessment | Requirements | Meaning |
| --- | ---: | --- |
| `supported` | 96 | Repository controls and evidence support the requirement in the reviewed scope. |
| `partial` | 69 | A reviewed control addresses part of the requirement. The row records the open evidence or coverage. |
| `deployment` | 22 | The claim requires evidence from the deployed environment. |
| `gap` | 15 | A required implementation or design control is missing. |
| `out-of-scope` | 51 | The relevant technology or role falls outside SprintFlow’s application surface. |

The matrix records a repository assessment. Level 2 conformance requires resolving the applicable gaps and verifying the remaining application and deployment evidence. The [C7 evaluation](../evaluation/advanced-security.md) records matrix validation and the security feature results.

## Existing control examples

| ASVS chapter | Control in SprintFlow | Evidence |
|---|---|---|
| V2 Validation and Business Logic | Request records with Bean Validation; server-owned fields cannot be mass-assigned; business rules (one active sprint, story points) enforced by the service and database constraints. Search input is bounded (query ≤ 200 characters, ≤ 8 words of ≤ 64 letters/digits, page ≤ 50, cursor ≤ 100 base64url characters) and reduced to letters and digits before it reaches `to_tsquery`, so no query syntax or SQL is ever taken from the user; parameters are bound, never concatenated | `RequestContractIntegrationTest`, `ProjectAccessIntegrationTest`, `SearchIntegrationTest`, `SearchQueryProperties`, `SearchQueryTest` |
| V3 Web Frontend Security | CSP (`default-src 'self'`, `object-src 'none'`, `frame-ancestors 'none'`), Permissions-Policy, CORP, enforced Trusted Types (`sprintflow-google`, `goog#html`, `'allow-duplicates'`). Search highlights arrive as plain-text segments with a match flag and are rendered as React text, never as HTML | `SecurityConfigurationTest`, `CspPolicyTest`, `scripts/verification/trusted-types.ts`, `CommandCenter.test.tsx` |
| V4 API and Web Service | RFC 9457 errors, If-Match on every mutation (428/412), rate limits with `Retry-After` | `ApiExceptionHandlerTest`, `RateLimitFilterTest` |
| V6 Authentication | BCrypt (cost 12); recovery reset does not reveal whether an account exists or has a code; credential routes rate-limited per address | `AccountSecurityIntegrationTest`, `RateLimitHttpTest` |
| V7 Session Management | Server-side sessions in PostgreSQL; id rotation at sign-in and password change; other sessions revoked on credential change | `AccountSecurityIntegrationTest` |
| V8 Authorization | One access context per request with declared needs; archive protection; admin hierarchy; per-role tests. Membership is matched on the immutable account id (`project_member.user_id`), so an email change cannot grant or lose access; task lookup by key answers 404 to non-members to prevent key enumeration. Search filters by membership inside the search statement (never after fetching), so hits, exact task keys, snippets and result counts from other projects are never returned; trashed projects and items are excluded; a forged keyset cursor only moves the window inside the caller's own results | `ProjectAccessIntegrationTest`, `ProjectKeyIntegrationTest`, `SearchIntegrationTest` |
| V11 Cryptography | 256-bit random bearer tokens, stored as SHA-256 digests. Secrets at rest use AES-256-GCM (JCA provider, no custom crypto) with a fresh 96-bit IV from `SecureRandom` per value and a 128-bit tag; version, key id and field context are authenticated data. Keys come from the environment (never source or database), are validated at startup (fail fast), carry a fingerprint key id, and rotate in two deploys (new key first readable everywhere, then current) through retired keys plus an idempotent re-encryption job (ADR 0015) | `SecureTokensTest`, `EncryptionServiceTest`, `KeyringTest`, `EncryptionProperties`, `ReEncryptionJobTest`, `ReEncryptionIntegrationTest` |
| V15 Secure Coding and Architecture | Dependency inventory and update process: CycloneDX SBOMs (jar, frontend, image), osv-scanner and Trivy gates, Dependabot with cooldown, SHA-pinned actions, digest-pinned images, signed images with SLSA provenance ([supply chain](supply-chain.md)) | `quality`, `security`, `image-scan` and `publish` CI jobs, `repository-check.ts` |
| V16 Security Logging and Error Handling | Security events logged without secrets; 500 responses carry an `errorId`, never internals | `SecurityConfigurationTest`, `ApiExceptionHandlerTest` |
| V4.4 WebSocket | Authenticated session and allow-listed origin at the handshake. CSRF token on STOMP CONNECT. Per-project membership controls subscriptions and supported SEND destinations. Frame size, subscription count and send buffers are bounded. See [ADR 0017](../adr/0017-realtime-stomp-and-sequence-numbers.md) and [ADR 0033](../adr/0033-shared-text-crdt.md). | `RealtimeWebSocketIntegrationTest`, `RealtimeEventsAccessIntegrationTest`, `RealtimeUnitTest` |
