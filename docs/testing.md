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

Surefire explicitly includes both `*Test` and `*Properties`, so a normal verification runs jqwik. Five classes contain fourteen properties:

- CSV: quoted-cell round trips and formula neutralisation, including invisible prefixes.
- PDF: wrapping fits the requested width and preserves visible text order.
- Rate limiting: arbitrary request timings obey a 30-attempt fixed refill window.
- Risk model (`risk/*Properties`): probabilities strictly in (0, 1), explanations are the largest log-odds terms, regularisation never increases the weight norm, training is deterministic; AUC equals the pairwise definition, is 1/0 for perfect/reversed rankers and invariant under increasing transforms; Brier is 0 for perfect predictions; calibration bins partition the predictions.
- Search (`search/SearchQueryProperties`, a jqwik fuzz of the parser until the Jazzer harness in C7): any text yields a well-formed, bounded `to_tsquery` argument of quoted prefix terms with no user-supplied operators, terms come from the input words, task keys are recognised in any case, cursors round-trip and arbitrary cursor text fails only as a 400 problem.

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

Frontend verification exposes `npm run check` (Vitest, TypeScript and Vite) and, since A8, `npm run e2e` ([end-to-end tests](#end-to-end-tests-a8)). CI's Docker job exercises account/workspace behaviour, uploads and persistence across a restart. A2 adds no views or endpoints. Its accessibility acceptance check aligns the profile button’s accessible name with its visible avatar initial and account name, including when the account name is hidden at narrow widths.

For A2 acceptance, the packaged app was also started with a separate disposable PostgreSQL database and checked with `scripts/verification/business-rules.ts` (real browser/database role and workflow checks; A8 ported it to `frontend/e2e/tests/roles.e2e.ts`). A temporary axe-core 4.14.0 scan covers sign-in and the empty overview in light/dark themes; all three scans reported zero violations after the profile-button fix. The audit browser allows axe script injection past CSP and uses reduced motion to inspect settled colours; application CSP is unchanged. This targeted scan does not claim a full WCAG audit. A8 made Playwright/axe permanent in `npm run e2e`.

## Content Security Policy and Trusted Types in a real browser

The CSP enforces Trusted Types (`require-trusted-types-for 'script'`) with only two policies: the app's `sprintflow-google` script-URL policy and Google Identity Services' own `goog#html`. `'allow-duplicates'` is included so GIS can create `goog#html` again without throwing. `CspPolicyTest` and `SecurityConfigurationTest` pin the header text. `scripts/verification/trusted-types.ts` checks the behaviour in Chromium: it loads the sign-in page of a running, built app under the app's own header and fails on any `securitypolicyviolation` event or Trusted Types console error.

```sh
cd scripts
npm ci && npx playwright install chromium   # once
# App running on SPRINTFLOW_QA_URL (default http://localhost:8081), for example via ./start.sh:
npm run trusted-types              # also loads GIS and renders its button; needs accounts.google.com
npm run trusted-types -- --offline # app only; Google requests are blocked
```

No account is used and nothing is written. The script answers the Google config request with a placeholder client id, so the server does not need `SPRINTFLOW_GOOGLE_CLIENT_ID`; set `SPRINTFLOW_TT_GOOGLE_CLIENT_ID` to render a real client's button. Because the default mode needs network access to Google, it is not part of required CI. Run it after changing the CSP, `googleScriptUrl.ts`, the GIS loader or the Google sign-in component. Its offline mode is also part of the e2e suite (`security-headers.e2e.ts`), and every e2e flow fails on a CSP or Trusted Types violation raised by the app.

## Module structure and event delivery (A3)

`ModuleArchitectureTest` runs Spring Modulith verification, rejects controller/repository coupling and field injection, and generates `docs/architecture/` (C4 PlantUML and module canvases). `ModuleEventsIntegrationTest` uses PostgreSQL, the actual JDBC publication registry, asynchronous application-module listeners and Scenario. It checks typed task movement, comment deduplication, publication/domain rollback, failed delivery, retry and duplicate replay. The simulated listener-outage log is expected in that test; its assertions require recovery and an empty publication entry afterwards.

```sh
cd backend
./mvnw -Dtest=ModuleArchitectureTest,ModuleEventsIntegrationTest test
./mvnw clean verify
```

Always use `clean` after moving Java packages; stale class files otherwise remain in `target/classes` and inflate JaCoCo's uncovered-class count. A3 preserves the 90% line/65% branch floors and the existing mutation scope, including the security-filter types moved from `common` into `auth/filter`. Frontend `npm run check` and the Playwright verification scripts were the available UI checks at the time; A8 added `npm run e2e`.

`TestContextShutdown` closes cached Spring contexts when the JUnit test plan finishes, before the JVM starts Testcontainers cleanup. Modulith checks outstanding publications during context shutdown, so PostgreSQL must still be available then. The test listener is registered only under `src/test/resources`; production lifecycle is unchanged. Do not run a dependency scanner that invokes Maven concurrently with verification after editing sources, because recompilation during a JaCoCo run invalidates class fingerprints.

A3 verification: 125 backend tests passed with 91.3% line coverage (3319/3634) and 70.2% branch coverage (963/1372). PIT detected 378 of 710 mutations (53.2%, including timeout detections), above the unchanged 50% floor. Frontend acceptance includes notification badges with unread counts: accessible names retain the visible number outside parentheses, including the `99+` cap. The focused axe regression is covered for English and Swedish.

## A4 platform migration and performance

Tests use Java 25, Boot 4 MVC/client test autoconfiguration and Testcontainers 2. Existing Mockito replacements use `@MockitoBean`/`@MockitoSpyBean`. `GoogleRestClientTransportTest` uses a local HTTP server to exercise actual timeout and response handling; Google claims remain covered by the replaceable verifier's security tests.

The k6 comparison uses the same fixture/load and JVM/database limits before/after; its raw points are excluded from Git and measured-only summaries are committed under `docs/evaluation/`. To repeat against a disposable app (never production):

```sh
BASE_URL=http://127.0.0.1:18084 k6 run --summary-export artifacts/perf-summary.json --out json=artifacts/perf-raw.json scripts/performance/workspace-read.k6.ts
node scripts/verification/summarize-performance.ts artifacts/perf-raw.json artifacts/perf-measured.json 'runtime and commit'
```

The fixture cleans up its own account/project on normal completion. Local measurements include uncontrolled host and cache effects; they validate the workload and provide evaluation data, not a production SLA or proof of scalability.

## Frontend checks (A5)

`npm run check` in `frontend` runs, in order: ESLint (`typescript-eslint` strict, `react-hooks` including the React Compiler rules, `jsx-a11y`, feature import boundaries; zero warnings) plus the boundary probe `scripts/check-boundaries.ts`, Prettier `--check`, `tsc --noEmit` (with `noUncheckedIndexedAccess` and `exactOptionalPropertyTypes`), Vitest, the Vite production build and `size-limit` (150 kB gzip JavaScript, 15 kB gzip CSS). `npm run format` rewrites formatting. The structure and rules are described in [ADR 0008](adr/0008-frontend-feature-folders-and-lint.md).

Tests sit next to the code they cover. Feature tests live in `src/features/<name>/`; App-level flows are split per area in `src/app/App.<area>.test.tsx` and share fixtures from `src/app/appTestFixtures.ts`. A5 moved and split files without behaviour changes: the existing tests only had their imports adapted.

```sh
cd frontend
npm ci
npm run check
npx vitest run src/features/tasks   # one feature
```

## End-to-end tests (A8)

`frontend/e2e` is a separate npm package with its own lockfile (Playwright 1.65, `@axe-core/playwright` 4.14, Lighthouse CI). The production build and the Docker image never install it. The suite runs against a real app and PostgreSQL; it never starts or mocks the backend.

```sh
./start.sh                                    # or any running app with a database
npm ci --prefix frontend/e2e
npx --prefix frontend/e2e playwright install chromium
E2E_BASE_URL=http://127.0.0.1:8081 npm run e2e --prefix frontend
```

| Test | Covers |
|---|---|
| `auth.e2e.ts` | Sign-up through the UI, sign-out (session gone: `/api/auth/me` is 401), wrong password shows an alert, sign-in, session survives reload |
| `workspace.e2e.ts` | Create a project, sprint, story and task through the dialogs; API confirms each lands in the right project; persistence after reload; planning the story into the sprint moves its task (sprint inheritance) |
| `roles.e2e.ts` | Owner/Admin/Member/Viewer/Outsider matrix, cross-project isolation, one active sprint, invalid associations (400), admin UI cannot transfer ownership, ownership transfer and back |
| `search.e2e.ts` | Ctrl+K palette: search hit with highlight, Enter opens the board narrowed to the task, a typed task key jumps directly, `?` help dialog with focus, `g o`; axe on the open palette (light and dark) and the help dialog |
| `security-headers.e2e.ts` | Sign-in page under the enforced CSP and Trusted Types (offline counterpart of `trusted-types.ts`) and security headers |

Every test checks, through auto fixtures, that the page raised no uncaught errors and no CSP or Trusted Types violations. A violation counts only when it comes from the app's own documents or scripts: code injected through the DevTools protocol (axe-core) has no source file and is ignored. `expectAccessible` runs axe-core with WCAG 2.0/2.1/2.2 A and AA tags. It fails on **serious or critical** findings, attaches the full list (including minor and moderate findings) to the HTML report, and asserts that axe actually evaluated rules. It scans the sign-in page and backlog in light and dark mode, and the overview, projects, sprints, backlog and tasks views in light mode. Accounts are disposable (`e2e-<label>-<timestamp>@example.com`) and each test deletes its projects and accounts, even on failure. With one worker the suite needs about 20 seconds.

Selectors use roles, labels and visible English text. `navigationItem` accepts a navigation button or a router link, so route-based navigation does not break the suite. **Visual regression** (screenshots in light, dark and mobile) is deliberately not here: it comes with the design system and Storybook in A7, so baselines are captured from the final components.

### What happened to `scripts/verification`

| Script | Status |
|---|---|
| `business-rules.ts` | Ported to `roles.e2e.ts` (role matrix, isolation, ownership) and `workspace.e2e.ts` (story to sprint with task inheritance); removed |
| `creation-visibility.ts` | Ported to `workspace.e2e.ts` (creation and persistence of all four entity types); removed. Choosing a destination project inside the forms is covered by Vitest (`ProjectDestination.test.tsx`, `EditorProject.test.tsx`); the injected post-save refresh failure was not ported |
| `version-headers.ts` | Moved into `frontend/e2e/support/api.ts`; removed |
| `trusted-types.ts` | Kept for the online mode, which renders Google Identity Services and needs network access to Google; the offline mode is in the suite |
| `responsive.ts`, `guidance.ts`, `responsive-fixtures.ts` | Kept until A7: layout matrices at ten widths with screenshots belong with visual regression |
| `container-smoke.ts` | Kept: it checks the container (non-root uploads, persistence across a Compose restart) in the required `docker` job |
| `repository-check.ts`, `summarize-performance.ts` | Kept: repository rules and k6 summaries, not browser tests |

## CI pipeline (A8)

`.github/workflows/quality.yml` keeps the four required jobs (`quality`, `docker`, `security`, `mutation`) and adds jobs that reuse the image built by `docker`:

| Job | What it does | Blocking |
|---|---|---|
| `quality` | Repository check, tooling type-check, frontend `npm run check`, e2e type-check, the pipeline's only `mvn verify`, CycloneDX SBOMs (backend from the jar, frontend from `cyclonedx-npm`) | Required |
| `docker` | Buildx build with a GHA layer cache, checks that the image has no curl or wget, Compose smoke test and restart, uploads the image tar | Required |
| `security` | gitleaks over history, osv-scanner over all lockfiles and `pom.xml` | Required |
| `mutation` | PIT on pure logic | Required |
| `e2e` | Compose with the built image, Playwright with axe | Not required yet |
| `image-scan` | Trivy image SBOM, HIGH/CRITICAL SARIF to code scanning, fails on fixable CRITICAL | Not required yet |
| `zap-baseline` | OWASP ZAP passive baseline with the AJAX spider; rules in `.zap/rules.tsv` | Not required yet |
| `k6-smoke` | `K6_PROFILE=smoke` workspace reads: 2 VUs for 10 s, no failed requests, p95 < 500 ms | Not required yet |
| `lighthouse` | Lighthouse CI budgets, below | Informational |
| `publish` | `main` pushes only: GHCR push, cosign signature, SLSA provenance and SBOM attestations | Runs after merge |

Maven, npm and Playwright browser downloads are cached. `codeql.yml` (actions, java-kotlin, javascript-typescript; no build) and `scorecard.yml` (OpenSSF Scorecard on `main`) are separate workflows. Supply-chain decisions are in [ADR 0014](adr/0014-ci-supply-chain.md) and [docs/security/supply-chain.md](security/supply-chain.md).

### Lighthouse budgets

`npm run lighthouse --prefix frontend/e2e` (`frontend/e2e/lighthouse.ts`) runs Lighthouse CI three times per profile against `E2E_BASE_URL` and judges the median run:

| Metric | Budget | Note |
|---|---|---|
| Largest Contentful Paint | < 2.5 s | |
| Total Blocking Time | < 200 ms | Lab proxy for INP < 200 ms, because INP needs real interactions |
| Cumulative Layout Shift | < 0.1 | |
| Transferred script | < 175 kB | `size-limit` guards 150 kB gzip at build time |

The desktop profile fails the job. The mobile profile (simulated slow 4G, 4x CPU slowdown) reports the same budgets as warnings: its median LCP on the sign-in page was 2.9–3.0 s locally and in CI, because the page renders only after the SPA bundle and Google Fonts load. Lighthouse also showed that the 449 kB bundle was served uncompressed. A8 enables gzip for JavaScript, CSS and SVG (about 141 kB), but not for HTML or JSON, so no compression side channel exposes CSRF tokens or nonces. Self-hosted fonts and a lighter first render are A7 work.

### k6 smoke

```sh
BASE_URL=http://127.0.0.1:8081 K6_PROFILE=smoke k6 run scripts/performance/workspace-read.k6.ts
```

The smoke profile reuses the A4 fixture (disposable account, 23-task project, cleanup in `teardown`) with three warm-up iterations and two virtual users for ten seconds. It is a regression tripwire, not a capacity measurement. The full profile (no `K6_PROFILE`) is unchanged.

## Search (B9)

`SearchIntegrationTest` runs `GET /api/search` against PostgreSQL with V16: Owner, Admin, Member and Viewer find hits in their project; an outsider gets no hits (and 403 with `projectId`), also not for an exact task key; archived projects stay searchable, trashed projects, trashed tasks and their comments do not appear. It also covers Swedish and English stemming, prefixes, a trigram typo, the type filter, highlight segments, the exact-key-first rule, keyset pages that return every hit exactly once, parameter validation as problems, and the query count: one statement for any number of projects and hits (three with `projectId`: user, access row, search). `SearchQueryTest`, `SearchCursorTest`, `SearchSupportTest` and `SearchQueryProperties` need no database; `SearchQuery`, `SearchCursor` and `SearchType` are in PIT's target classes.

In the frontend, `features/search/*.test.tsx` cover the palette (combobox ARIA, debounced and aborted requests, highlights rendered as text, keyset "show more", exact task key on Enter, commands), the shortcut listener (ignored while typing or behind a dialog) and the help dialog's focus trap in English and Swedish; `app/App.search.test.tsx` covers navigation from a hit to the board narrowed to its task.

```sh
./mvnw -Dtest='SearchIntegrationTest,Search*Test,SearchQueryProperties' test
```
