# Advanced security verification

| Check | Result | Reproduction |
| --- | --- | --- |
| Audit-chain property | 200 jqwik cases with seed `2026112107`, each over a chain of up to 30 events. Checked content changes, predecessor changes, incorrect positions and invalid hashes. | `./mvnw -Dtest=AuditChainHashTest test` from `backend`. |
| Focused audit-chain mutation score | PIT killed 11 of 11 mutations generated for `AuditChainHash` with `STRONGER`. The focused score is 100% against the 70% gate. | PIT configuration in `backend/pom.xml`, narrowed to `AuditChainHash` and `AuditChainHashTest` for the focused run. |
| PostgreSQL, security and API | 22 focused tests passed. Exercises replay, expiration, account ownership, role checks, archived projects, transactional writes, audit retention, damaged rows and anchors, key rotation, MFA and OpenAPI. | Focused Maven command below. |
| Browser and accessibility | Three Chromium flows passed against the application and PostgreSQL. A CTAP2 virtual authenticator performed registration and verified sign-in, followed by credential deletion. The import workflow verified the retained audit chain and checked axe. | Browser command below. |
| ASVS inventory | The 253 unique rows match the 70 level 1 and 183 level 2 IDs in the pinned OWASP English CSV. Every evidence path resolved in the repository. | [Matrix](../security/asvs-5-l2.csv) and [official CSV release](https://github.com/OWASP/ASVS/releases/tag/v5.0.0_release). |
| Frontend | 319 Vitest tests passed. Initial JavaScript is 123.85 kB gzip and lazy passkey code totals 2.96 kB gzip. | `npm --prefix frontend run check`. |

Run the focused Java suite with Docker available for PostgreSQL Testcontainers.

```sh
cd backend
./mvnw -B -ntp -Dtest=PasskeyHttpIntegrationTest,AuditChainHashTest,AuditIntegrityIntegrationTest,AdminAuditIntegrationTest,MfaHttpIntegrationTest,ReEncryptionIntegrationTest,OpenApiContractTest,SecurityConfigurationTest test
```

For the focused PIT score, copy `pom.xml` next to the original, set the PIT plugin’s `targetClasses` to `com.sprintflowinsight.common.audit.AuditChainHash` and `targetTests` to `com.sprintflowinsight.common.audit.AuditChainHashTest`. Run `./mvnw -f focused-pom.xml -B -ntp test-compile org.pitest:pitest-maven:mutationCoverage` from `backend`. The main CI configuration includes the algorithm in its broader mutation run.

Run the browser checks against the built app using its configured localhost origin. The virtual authenticator tests require Chromium.

```sh
npm ci --prefix frontend/e2e
E2E_BASE_URL=http://localhost:8081 npm --prefix frontend run e2e -- passkeys.e2e.ts import-export.e2e.ts
```

The [nightly workflow](../../.github/workflows/fuzz-nightly.yml) runs the CSV import, Jira import and automation condition fuzz tests at 01:35 UTC and retains the test output. It can also run on demand after merge. Each production parser target has a 30-second fuzz budget.

The ASVS inventory is pinned to `OWASP_Application_Security_Verification_Standard_5.0.0_en.csv` from release `v5.0.0_release`, SHA-256 `6124dba176dc563f66363a11ae0c47f9b86b8a4a84c66a793670bd196ed86cd5`. Compare the matrix IDs and levels to the release rows whose level is 1 or 2. Resolve evidence paths from the repository root. The [assessment](../security/asvs.md) records remaining gaps and deployment verification.
