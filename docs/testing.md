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

## Frontend routes, data and accessibility (A6)

`npm run check` is unchanged in order; ESLint now also enforces `react-hooks/set-state-in-effect`, and size-limit checks several budgets instead of one: the entry chunk (125 kB gzip), the tasks and backlog/sprints/metrics/updates route chunks (20 kB each), all JavaScript together (210 kB) and CSS (15 kB). See [ADR 0013](adr/0013-router-query-and-typed-lexicon.md).

- **MSW.** `src/test/setup.ts` starts one MSW server for every test file and fails on unhandled requests. Tests answer API calls with `mockApi((path, request) => payload | HttpResponse)` from `src/test/api.ts`, so they go through the real `fetch` and REST client (CSRF, error mapping). Component tests that mock the `api` module at module level remain where they test a single component.
- **Providers.** `renderWithProviders` (`src/test/render.tsx`) adds the query client, language, theme and a memory router; `renderWorkspace` (`src/app/testWorkspace.tsx`) renders the workspace routes for a fixed session. Views are lazy chunks, so the Testing Library wait time is raised to five seconds for their first import.
- **Routing.** `App.routing.test.tsx` covers the old `?report=`/`?resetToken=` redirects, deep links, the signed-out deep link, unknown paths, one `<main>`, the skip link and focus on the page heading after navigation.
- **Lexicon.** `shared/i18n/lexicon.test.ts` checks plurals, locale dates and numbers, and uses `@ts-expect-error` to prove that an unknown key, a missing Swedish key and a missing argument do not compile.
- **Accessibility.** `App.a11y.test.tsx` runs axe-core (WCAG 2.0–2.2 A/AA tags) on sign-in in both languages with the dark theme, password reset, overview, tasks, backlog, projects, profile, the mobile menu and a shared report, and fails on serious or critical findings. `src/test/axe.test.ts` proves the helper catches violations. jsdom has no layout, so colour contrast and target size are left to the browser checks below.
- **Virtualization.** `app/Virtualization.test.tsx` renders 5,000 tasks and 5,000 stories with a simulated viewport (jsdom has no layout). The board renders 28 cards and about 1,500 DOM nodes, only rendered cards request attachments, and the backlog renders a bounded window of rows with `aria-rowcount=5001`.

For A6 the Playwright checks that use API fixtures were run against the production build served by `npx vite preview --port 4173` (`SPRINTFLOW_QA_URL=http://127.0.0.1:4173`): `responsive.ts` passed 1,110 layout checks (touch targets now include navigation links), and `guidance.ts` passed on all 11 pages. A one-off browser run with 5,000 fixture tasks rendered 24 cards and 1,385 DOM nodes. All six real-backend E2E flows (`npm run e2e`) subsequently passed both locally and in CI against the packaged Java app and PostgreSQL. A real-data Chromium check also verified 5,000 tasks and 5,000 stories (24 mounted cards and 12 rows), Home/End to both list edges, Swedish ICU plurals and an anonymous report link. `shared/i18n/icu.test.ts` checks genuine compiled ICU patterns; `npm run messages:check` rejects stale generated TypeScript.

## Design system and visual baselines (A7)

`npm run check --prefix frontend` includes shared chart/state tests and the existing feature/route regressions. `npm run storybook:build --prefix frontend` builds the separate component catalogue; it is never shipped in the Spring jar. The five SVG chart types all include table alternatives and keyboard data exploration.

```sh
npm ci --prefix frontend/e2e
npm run storybook:build --prefix frontend
npm run visual --prefix frontend
# Only when intentionally changing the approved component appearance:
npm run visual --prefix frontend/e2e -- --update-snapshots
```

Playwright compares every chart/state story in light and dark against the tracked PNGs in `frontend/e2e/visual/snapshots/`, then checks axe, complete data tables and keyboard selection. Review updated PNGs before committing them. CI's `storybook` job uploads failed comparisons/traces in `storybook-visual-report`; it does not regenerate expected images. Fonts are self-hosted and tests wait for `document.fonts.ready` for deterministic layout. The ordinary `npm run e2e` suite still runs against the real application and PostgreSQL.

The production-server [chart E2E flow](../frontend/e2e/tests/charts.e2e.ts) creates a project and task through the real API, reads the migrated burndown, checks Home/End and its complete table, and runs axe in light and dark themes. It complements the isolated Storybook comparisons.

## Search (B9)

`SearchIntegrationTest` runs `GET /api/search` against PostgreSQL with V16, V17 and V18: Owner, Admin, Member and Viewer find hits in their project; an outsider gets no hits (and 403 with `projectId`), also not for an exact task key; archived projects stay searchable, trashed projects, trashed tasks and their comments do not appear. Epic tests cover all membership roles, archived/trash boundaries, the type filter, indexing and highlighting. `EpicStorageIntegrationTest` verifies persistence and the same-project story foreign key. It also covers Swedish and English stemming, prefixes, a trigram typo, the type filter, highlight segments, the exact-key-first rule, keyset pages that return every hit exactly once, parameter validation as problems, and the query count: one statement for any number of projects and hits (three with `projectId`: user, access row, search). `SearchQueryTest`, `SearchCursorTest`, `SearchSupportTest` and `SearchQueryProperties` need no database; `SearchQuery`, `SearchCursor` and `SearchType` are in PIT's target classes.

In the frontend, `features/search/*.test.tsx` cover the palette (combobox ARIA, debounced and aborted requests, highlights rendered as text, keyset "show more", exact task key on Enter, commands), the shortcut listener (ignored while typing or behind a dialog) and the help dialog's focus trap in English and Swedish; `app/App.search.test.tsx` covers navigation from a hit to the board narrowed to its task.

```sh
./mvnw -Dtest='SearchIntegrationTest,Search*Test,SearchQueryProperties' test
```

## Realtime verification (B1)

`RealtimeWebSocketIntegrationTest` uses real sockets and PostgreSQL to verify handshake/session/Origin/CSRF rejection, denied subscriptions/SEND and no ghost presence after an outsider's rejected frame. Owner/Admin/Member/Viewer and archived-project readers report and clear an open task. Rollback, ordered event delivery, gap filling, logout and membership removal are covered. `RealtimeUnitTest` rejects foreign, missing and trashed task ids; `PresenceRegistryTest` covers multiple tabs and task cleanup. `RealtimeSequenceIntegrationTest` exercises concurrent publishers, duplicate delivery, retention and resync against PostgreSQL. V19 follows the existing V16–V18 search migrations.

Frontend tests verify project-scoped Query invalidation, specific comment-thread refreshes, notification invalidation, coalescing, reconnect/backoff and heartbeat behavior. The MSW board regression checks that a data refresh does not reload project roles. `frontend/e2e/tests/realtime.e2e.ts` uses two authenticated browser contexts with actual REST/WebSocket traffic: a member moves a task and creates/edits/deletes a comment, the other browser updates without losing its local draft, presence tracks the open task, and reconnect fills a real gap. Fault injection closes the real socket while its browser is offline; no response is mocked. Axe runs on both boards and the live thread.


## B3 roadmap verification

`RoadmapAccessIntegrationTest` exercises the new endpoints as Owner, Admin, Member, Viewer, outsider and against archived/trashed projects. `RoadmapMutationIntegrationTest` covers stale revisions (including deletion/replacement without revision reuse), opposite concurrent edges, cycles through trashed work and invalid estimates. `RoadmapReadIntegrationTest` verifies progress, outside blockers, incomplete forecasts and bounded query count with 1,000 tasks. `RoadmapContractTest` checks documented record fields; `SchemaMigrationTest` preserves pre-V20 estimates on upgrade.

Pure scheduling properties compare DFS with an independent transitive-closure oracle and critical duration with exhaustive paths; unit cases include a 20,000-node chain, normal-CDF reference values and weekday boundaries. PIT now targets `workspace.roadmap.schedule.*` as part of the repository's mutation check.

`frontend/e2e/tests/roadmap.e2e.ts` runs against the packaged app and PostgreSQL. It creates epics, assigns stories, rejects a cycle while retaining choices, enters estimates, reads PERT/table/arrows, follows a deep link through reload, completes a blocker on the board and checks Viewer/archived views. It also runs axe in editor, desktop and mobile states, in English and Swedish and both themes. The separate responsive/guidance sweeps use API fixtures and include the new page and four editor types.

## Flow analysis (B5)

`flow/*Test` and `FlowProperties` compare fixed histories, independent hourly ledgers, type-7 percentiles, NIST reference limits and all configured XmR rules. `FlowIntegrationTest` exercises PostgreSQL as Owner/Admin/Member/Viewer/outsider and in archived/trashed projects, missing history, invalid limits, concurrent and stale collection revisions (including clearing), and a constant query count with 1,000 extra tasks. `FlowContractTest` checks every new request/response record against OpenAPI; `SchemaMigrationTest` verifies V21 upgrades and project-scoped constraints. The frontend MSW tests cover EN/SV, window routing, read-only settings, rejected drafts and WIP warnings independent of board filtering.

`frontend/e2e/tests/flow.e2e.ts` uses real REST/history, charts and their complete table/keyboard controls, window links and reload, WIP editing, advisory board warnings, two administrators with a stale draft, Viewer/archived views, axe and Swedish mobile dark mode. No response is mocked. The responsive and guidance sweeps also include `/flow` and the WIP editor.

For the performance profile, start the packaged application and a disposable local PostgreSQL database. Node 22+ and k6 are required. Use the PostgreSQL container attached to that exact application; the fixture verifies its newly created API project and owner in the database before inserting anything. It creates its own disposable account, 1,000 tasks and about 49,000 status snapshots across 730 days. Repeated same-status snapshots exercise history collapse.

```sh
export BASE_URL=http://127.0.0.1:8081
export FLOW_DB_CONTAINER="$(docker compose -p sprintflow-ci ps -q postgres)"
export FLOW_FIXTURE_FILE="$PWD/artifacts/flow-performance/fixture.json"
node scripts/performance/flow-fixture.ts seed
k6 run --summary-export artifacts/flow-performance/summary.json scripts/performance/flow-read.k6.ts
node scripts/performance/flow-fixture.ts cleanup
```

An isolated local PostgreSQL server can instead use `FLOW_PG_URL=postgresql://postgres@127.0.0.1:15495/b5_qa` with `psql` installed. Both application and database URLs must be local. `FLOW_TASKS` optionally raises the fixture to 10,000 tasks. The ignored fixture file contains disposable credentials and must not be uploaded; CI uploads only k6 summaries and always runs cleanup. The file is created exclusively with mode 0600, so even a concurrent creator or symlink cannot be overwritten. Both tools reject non-local origins and redirects. Storing the newly created session and reading it back for local authenticated test requests is intentional; no existing user credentials or application records are exported. Cleanup validates metadata and ownership before removing only the fixture-owned project and account.

The profile warms up three iterations, then uses two concurrent users for 20 seconds, reading both 30- and 180-day windows. It requires zero request failures, correct coverage/window responses and measured p95 below 500 ms. The initial PostgreSQL 16.10 container run (48,898 history rows) passed with p95 313.63 ms over 140 measured reads. A PostgreSQL 16 local rerun (48,883 rows) passed with p95 281.51 ms over 172 measured reads. These are local reproducibility measurements, not a production capacity claim. The `k6-smoke` CI job runs the same fixture/profile against the built container.

## Monte Carlo delivery forecasts (B6)

`MonteCarloSimulationTest` enumerates all four two-day binary paths independently and verifies exact tail probabilities, conservative amounts and censored dates. Other cases cover constant-capacity closed forms, shared paths across question changes, empty history, zero targets, horizon clipping and bounded histograms over counts exceeding an integer. `ForecastWiringProperties` uses fixed seeds to compare weekly bins with a calendar ledger and 10,000-path forecasts with closed-form constant-capacity answers.

`ForecastIntegrationTest` reads the actual PostgreSQL history with every membership role, outsider/anonymous and archive/trash boundaries. It covers weekly/velocity inputs, explicit and active-sprint targets, cross-project epic rejection, epic work and taskless stories, cache refresh after work/title changes, ignored Done snapshots/future transitions, parameter limits and query count with 1,000 unfinished tasks. `ForecastContractTest` checks every request/response record and warning enum against OpenAPI. No migration is added because B6 uses the existing V21 schema.

`features/forecast/ForecastView.test.tsx` covers EN/SV, chart tables and axe, applied URL queries/deep links, unit-dependent draft resets, empty history, the date field's accessible name and a correctable query error.

`frontend/e2e/tests/forecast.e2e.ts` runs against the packaged application and PostgreSQL without mocking API responses. It creates a disposable project and backdates only that project's API-created task/sprint history after checking its test owner. It checks throughput, velocity, epic and sprint scope, custom questions, a past-date validation error focused on the visible field and corrected without losing the draft, reloadable links, project switching, chart keyboard/table alternatives, EN desktop, SV dark mobile, Viewer and archived reads, and axe. Set `E2E_DB_CONTAINER` to that local app's PostgreSQL container; the default uses the repository's Compose service. CI selects its own `sprintflow-ci` container explicitly.

The real registration limit remains enabled during browser tests. Disposable accounts share the runner's client address, so the registration helper accepts one `429 RATE_LIMITED` response, validates `Retry-After` as 1–60 seconds, waits that interval and retries once. Other failures are immediate. The 120-second test timeout allows one refill window plus the browser flow; the application limit stays at 20 registrations per minute.

Local B6 verification passed 570 backend tests and 257 frontend tests with coverage gates. The new simulation engine's targeted PIT run killed 94 of 115 mutations (82%, none uncovered). The fixture-based responsive sweep passed 1,398 layout checks; guidance passed all 14 pages, 44 empty forms and 544 expanded-help layouts. These layout fixtures complement the real-database browser tests; they do not verify persistence. Final whole-repository mutation and security results are available in the PR checks.

## B7 planning poker

`workspace/poker/PokerStatisticsTest` exercises frozen decks, invalid custom cards, abstentions, independent expected statistics, T-shirt ordering, safe numeric mapping, serialization and round transitions. `PokerIntegrationTest` uses real PostgreSQL and the authenticated API for every endpoint across Owner/Admin/Member/Viewer, outsider and archived/trashed projects. It checks caller-only cards before reveal, summary-only lists, hidden second-round votes, delayed commands, atomic vote/reveal races, story revision conflicts, explicit T-shirt mapping, cancellation cleanup, round limits, composite project foreign keys and deletion cascades. `PokerEventTest` asserts the exact metadata-only event shape; `PokerContractTest` checks every public record against OpenAPI. The server route/security regression includes `/poker?session=1` with a fresh CSP nonce and no-store response.

`features/poker/PokerView.test.tsx` covers voting/retraction, reveal/revote history, explicit story reload while preserving an unsaved estimate, custom-deck validation and failed drafts, revoked access, EN/SV read-only states and axe. Realtime hook tests ensure poker events refresh only that project's poker queries, leaving task, role and search queries untouched. The command-palette test waits for the modal's asynchronous focus effect instead of asserting before it has run.

`frontend/e2e/tests/poker.e2e.ts` uses the packaged app and actual PostgreSQL, sessions and WebSockets. Two browsers cast hidden votes, reveal disagreement, revote to consensus, save story points and read the activity record and persisted history after reload. It inspects actual socket frames for metadata-only invalidations, checks a Viewer, and exercises custom cards and archived reads on a Swedish dark mobile screen. Axe scans voting, results, history, custom forms and read-only views. B7's V23 was also started against an isolated migration set containing B16's V22 from PR #70; both applied successfully in order.

## B16 custom fields

`FieldConditionTest` and `FieldDefinitionRequestTest` cover typed operators, null semantics, exact decimals, size limits and definition normalization. `CustomFieldMigrationTest` upgrades V21 data and checks composite scope/type constraints, finite values, removal/rejoin and cascades. `CustomFieldIntegrationTest` covers every type, role/archive/trash boundaries, optimistic conflicts, atomic invalid snapshots, concurrent writes and bounded queries for 1,000-task CSV export. `CustomFieldQueryIntegrationTest` compares PostgreSQL filtering with the independent condition matcher and checks exact-key search, decimal/member CSV output and formula protection. OpenAPI contract checks cover the new request/response records and enums.

Frontend MSW tests exercise definition creation, immutable types, stale drafts, exact decimal strings, scoped field search, board filters and matching exports. `frontend/e2e/tests/custom-fields.e2e.ts` runs the actual definition/value/filter/search/CSV/conflict flow against the packaged server and PostgreSQL, including axe, Swedish dark mobile rendering and direct-route reload. The guidance and responsive probes include `/fields` and the two new drawer forms.

A field filter with an incomplete operand or a failed match request never falls back to unfiltered or cached matches; exports stay disabled until the condition succeeds or is cleared. The MSW board test and the real browser flow both cover deletion of an already selected field and recovery via **Clear field filter**. The additional exploratory browser probe also verifies 80-character names and 2,000-character text on mobile, recycle-bin option protection and value restoration.
