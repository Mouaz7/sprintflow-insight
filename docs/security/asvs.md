# OWASP ASVS 5.0 coverage

This file tracks how SprintFlow meets OWASP ASVS 5.0 level 2, chapter by chapter. Each entry links the control to the code or test that proves it. Phases add rows, and phase C7 completes the matrix.

| ASVS chapter | Control in SprintFlow | Evidence |
|---|---|---|
| V2 Validation and Business Logic | Request records with Bean Validation; server-owned fields cannot be mass-assigned; business rules (one active sprint, story points) enforced by the service and database constraints | `RequestContractIntegrationTest`, `ProjectAccessIntegrationTest` |
| V3 Web Frontend Security | CSP (`default-src 'self'`, `object-src 'none'`, `frame-ancestors 'none'`), Permissions-Policy, CORP, enforced Trusted Types (`sprintflow-google`, `goog#html`, `'allow-duplicates'`) | `SecurityConfigurationTest`, `CspPolicyTest`, `scripts/verification/trusted-types.ts` |
| V4 API and Web Service | RFC 9457 errors, If-Match on every mutation (428/412), rate limits with `Retry-After` | `ApiExceptionHandlerTest`, `RateLimitFilterTest` |
| V6 Authentication | BCrypt (cost 12); recovery reset does not reveal whether an account exists or has a code; credential routes rate-limited per address | `AccountSecurityIntegrationTest`, `RateLimitHttpTest` |
| V7 Session Management | Server-side sessions in PostgreSQL; id rotation at sign-in and password change; other sessions revoked on credential change | `AccountSecurityIntegrationTest` |
| V8 Authorization | One access context per request with declared needs; archive protection; admin hierarchy; per-role tests | `ProjectAccessIntegrationTest` |
| V11 Cryptography | 256-bit random bearer tokens, stored as SHA-256 digests | `SecureTokensTest` |
| V16 Security Logging and Error Handling | Security events logged without secrets; 500 responses carry an `errorId`, never internals | `SecurityConfigurationTest`, `ApiExceptionHandlerTest` |
