# Backend testing

## Requirements and commands

Use Java 25 and a running Docker daemon. On Windows start Docker Desktop with Linux containers and use `mvnw.cmd`; on Linux/macOS use `./mvnw`. No database URL, password, `.env` import or manually started PostgreSQL service is required for tests. If the surrounding shell exports `DEBUG`, unset it before verification: Spring Boot interprets it as debug mode, which makes the HTTP test client's body logging fail the log-secrecy assertion.

From the repository root:

```sh
npm ci --prefix frontend
npm run check --prefix frontend
cd backend
./mvnw clean verify
```

The frontend build is needed to package the UI; backend-only tests can run with `./mvnw test`. A cold run downloads Maven dependencies and the pinned PostgreSQL/Toxiproxy images. Docker image pulls, CPU and filesystem speed affect elapsed time. Database tests fail when Docker is unavailable; they never silently skip or fall back to H2.

## Database and feature suites

`PostgresTestContainer` starts one `postgres:16.10-alpine` instance per JVM, wired to Spring through container-backed `JdbcConnectionDetails`. Override the image explicitly with `-Dpostgres.test.image=postgres:16.x-alpine` when testing an upgrade; CI uses the pinned default. Testcontainers cleans up disposable containers when the JVM exits. PostgreSQL durability settings are relaxed only in the shared throwaway test container.

Eight HTTP suites extend `HttpFlowTest`: accounts, projects, backlog, sprints, tasks, collaboration, metrics and recycle bin. Common authentication, CSRF and fixture helpers live in the base. Existing account-security tests cover Google verification, recovery-code reset and password changes invalidating other JDBC sessions; request-contract tests cover typed preferences. Spring contexts share a database, so fixtures must use unique names and must not assume empty tables. Do not enable parallel suite execution without isolating these shared fixtures.

`SchemaMigrationTest` uses one disposable schema per test. It checks V7→V8 conflicts, the real partial unique index, V11 backlog ordering and the previous release (V12 with Java V8) upgraded to the latest schema with representative users/projects/sprints/stories/tasks. That upgrade explicitly tests validation failure, V8 history repair and preservation of data and versions. The current N-1 **release** baseline is V12; it is not automatically “latest migration minus one.” Update the fixture deliberately when the previous release advances. Existing installations must follow [ADR 0005](adr/0005-postgresql-tests-and-sql-migrations.md).

Run a feature or migration suite:

```sh
./mvnw -Dtest=ProjectFlowTest,SchemaMigrationTest test
```

## Property and fault tests

Surefire explicitly includes both `*Test` and `*Properties`, so a normal verification runs jqwik. Three classes contain four properties, generating 4,800 examples per full run:

- CSV: quoted-cell round trips and formula neutralisation, including invisible prefixes.
- PDF: wrapping fits the requested width and preserves visible text order.
- Rate limiting: arbitrary request timings obey a 30-attempt fixed refill window.

jqwik reports the seed and a shrunk counterexample on failure. To replay it, temporarily set `seed = "<reported-seed>"` on the failing `@Property`, then run `./mvnw -Dtest=CsvCellProperties test` (substitute the affected class). Remove the fixed seed after diagnosis. See the [jqwik guide](https://jqwik.net/docs/1.10.1/user-guide.html#optional-property-attributes). The local `.jqwik-database` is ignored by Git.

`DatabaseResilienceTest` uses a separate PostgreSQL instance and Docker network, with `ghcr.io/shopify/toxiproxy:2.12.0` in front of the database. It injects latency and disables the proxy, asserts a generic `503 SERVICE_UNAVAILABLE` with `Retry-After: 5`, checks health becomes DOWN and then UP, and verifies recovery without restarting the app. It also checks an existing JDBC session over real HTTP. Proxy faults are removed after every test; other suites use the shared database directly and are unaffected. The filter cannot change a response whose headers/body have already been committed.

```sh
./mvnw -Dtest=CsvCellProperties,PdfWrapProperties,RateLimiterProperties test
./mvnw -Dtest=DatabaseResilienceTest test
```

## Coverage and mutation reports

Every `verify` creates `backend/target/site/jacoco/index.html`, XML and CSV reports, and enforces the bundle's line and branch coverage floors from `pom.xml`. All production classes count; generated accessors and controllers are not excluded to raise the score. Use `clean verify` for a fresh baseline rather than accumulating execution data from earlier runs.

The A2 baseline measured about 91.6% line coverage and 70.0% branch coverage; the enforced floors are **90% lines** and **65% branches**. These leave a small margin while preventing an unnoticed regression.

PIT targets `common.*`, metrics, export filters, upload validation, PDF rendering and CSV escaping with the `STRONGER` mutators. It uses unit/property tests and excludes HTTP, integration, migration, security-configuration and fault suites, keeping the mutation run independent of Docker. Its mutation-score floor is configured in `pom.xml`. This is a bounded measure of selected logic, not the whole application. Surviving and uncovered mutants remain visible in the report.

The initial A2 PIT baseline detected 376 of 707 mutants (53.2%, including four timeout detections), with 270 survivors and 61 uncovered mutants; the floor is **50%**. The remaining mutants are a visible test-improvement backlog. This A2 baseline does not replace the later C-phase requirement of at least 70% PIT on each algorithm package. The report's test-strength percentage excludes uncovered mutants and differs from the enforced mutation score.

```sh
# Full suite, JaCoCo checks, then PIT:
./mvnw -Pmutation verify
# Only compile and run the bounded mutation suite (used by CI):
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage
```

Open `backend/target/pit-reports/index.html` (XML is in the same directory). CI runs normal verification and mutation testing as separate jobs, and uploads `backend-test-reports` and `backend-mutation-report` artifacts for seven days. Changing a floor is a reviewed change: explain the change in scope or new baseline rather than overriding the property to bypass a failing build.

Frontend verification currently exposes `npm run check` (Vitest, TypeScript and Vite); there is no `npm run e2e` script. Existing Playwright workflow checks live under `scripts/verification`, and CI's Docker job exercises account/workspace behaviour, uploads and persistence across a restart. A2 adds no views or endpoints. Its accessibility acceptance check aligns the profile button’s accessible name with its visible avatar initial and account name, including when the account name is hidden at narrow widths.

For A2 acceptance, the packaged app was also started with a separate disposable PostgreSQL database and checked with `scripts/verification/business-rules.ts` (real browser/database role and workflow checks). A temporary axe-core 4.14.0 scan covers sign-in and the empty overview in light/dark themes; all three scans reported zero violations after the profile-button fix. The audit browser allows axe script injection past CSP and uses reduced motion to inspect settled colours; application CSP is unchanged. This targeted scan does not claim a full WCAG audit. Permanent Playwright/axe wiring and `npm run e2e` remain the explicit scope of A8 (#9).

## Module structure and event delivery (A3)

`ModuleArchitectureTest` runs Spring Modulith verification, rejects controller/repository coupling and field injection, and generates `docs/architecture/` (C4 PlantUML and module canvases). `ModuleEventsIntegrationTest` uses PostgreSQL, the actual JDBC publication registry, asynchronous application-module listeners and Scenario. It checks typed task movement, comment deduplication, publication/domain rollback, failed delivery, retry and duplicate replay. The simulated listener-outage log is expected in that test; its assertions require recovery and an empty publication entry afterwards.

```sh
cd backend
./mvnw -Dtest=ModuleArchitectureTest,ModuleEventsIntegrationTest test
./mvnw clean verify
```

Always use `clean` after moving Java packages; stale class files otherwise remain in `target/classes` and inflate JaCoCo's uncovered-class count. A3 preserves the 90% line/65% branch floors and the existing mutation scope, including the security-filter types moved from `common` into `auth/filter`. Frontend `npm run check` and the existing Playwright verification scripts remain the available UI checks; adding the permanent `npm run e2e` entry point remains A8's scope.

`TestContextShutdown` closes cached Spring contexts when the JUnit test plan finishes, before the JVM starts Testcontainers cleanup. Modulith checks outstanding publications during context shutdown, so PostgreSQL must still be available then. The test listener is registered only under `src/test/resources`; production lifecycle is unchanged. Do not run a dependency scanner that invokes Maven concurrently with verification after editing sources, because recompilation during a JaCoCo run invalidates class fingerprints.

A3 verification: 125 backend tests passed with 91.3% line coverage (3319/3634) and 70.2% branch coverage (963/1372). PIT detected 378 of 710 mutations (53.2%, including timeout detections), above the unchanged 50% floor. Frontend acceptance includes notification badges with unread counts: accessible names retain the visible number outside parentheses, including the `99+` cap. The focused axe regression is covered for English and Swedish.

## A4 platform migration and performance

Tests use Java 25, Boot 4 MVC/client test autoconfiguration and Testcontainers 2. Existing Mockito replacements use `@MockitoBean`/`@MockitoSpyBean`. `GoogleRestClientTransportTest` uses a local HTTP server to exercise actual timeout and response handling; Google claims remain covered by the replaceable verifier's security tests.

The k6 comparison uses the same fixture/load and JVM/database limits before/after; its raw points are excluded from Git and measured-only summaries are committed under `docs/evaluation/`. To repeat against a disposable app (never production):

```sh
BASE_URL=http://127.0.0.1:18084 k6 run --summary-export artifacts/perf-summary.json --out json=artifacts/perf-raw.json scripts/verification/performance.ts
node scripts/verification/summarize-performance.ts artifacts/perf-raw.json artifacts/perf-measured.json 'runtime and commit'
```

The fixture cleans up its own account/project on normal completion. Local measurements include uncontrolled host and cache effects; they validate the workload and provide evaluation data, not a production SLA or proof of scalability.
