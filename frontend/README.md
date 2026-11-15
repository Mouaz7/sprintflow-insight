# Frontend

React/TypeScript with Vite. See the [main guide](../README.md) for the complete Docker start and configuration.

For development, use Node 24 and start the backend with `start.cmd -Native` from the repository root. In this directory:

```sh
npm ci
npm run dev
npm run check
```

The development server proxies API requests to Spring Boot on port 8081. `npm run check` runs ESLint, Prettier, the TypeScript check, Vitest, the production build of `dist` (which Maven includes in the server package) and the bundle size limits; `npm run format` fixes formatting. Code is organised in `src/app`, `src/features/<name>`, `src/shared` and `src/api` ([ADR 0008](../docs/adr/0008-frontend-feature-folders-and-lint.md)). Do not commit `node_modules` or `dist`.

### Responsive layout verification

With the app running, run `npm run responsive --prefix scripts` from the repository root
(or `npm run responsive` from `scripts`). This uses Playwright with isolated API fixtures;
no real account or project data is modified. Install a compatible Chromium with
`npx playwright install chromium` from `scripts` if needed.

`SPRINTFLOW_QA_URL` selects the app URL (default `http://127.0.0.1:8081`).
`PLAYWRIGHT_EXECUTABLE_PATH` optionally selects an existing Chromium executable.
The check covers all 11 workspace views, both languages/themes, empty/populated workspaces,
320–2560 px, breakpoint edges, landscape, an emulated 200% desktop zoom viewport,
touch targets, menus, keyboard focus, panels, notifications and login.
Screenshots and JSON results are written to `artifacts/responsive/`.
After a Docker build, rerun against port 8081 to verify the served production assets.

### End-to-end tests

`e2e/` is a separate Playwright package (its own lockfile, so the production build
and Docker image never install test tooling). With the app and database running
(`./start.sh`, or Docker Compose as in CI):

```sh
npm ci --prefix e2e
npx --prefix e2e playwright install chromium
E2E_BASE_URL=http://127.0.0.1:8081 npm run e2e
```

The suite registers disposable accounts against the real backend and database, covers
sign-up, sign-out and sign-in, project/sprint/story/task creation with persistence after
reload, planning a story into a sprint, the project role matrix and ownership transfer,
and runs axe-core on each visited view in light and dark mode. Serious or critical axe
findings fail the test. Every test removes the accounts and projects it created.
The HTML report is written to `e2e/playwright-report/`. See
[the testing guide](../docs/testing.md#end-to-end-tests-a8).

### Examples and guidance

All eleven workspace pages include keyboard-operable help. Creation/editing forms
show contextual examples in empty fields and expandable explanations in Swedish
and English. Examples are placeholders, never prefilled or saved data.

Run `npm run guidance --prefix scripts` against the local app. This uses API fixtures
to check expanded help at mobile, tablet, desktop and landscape sizes, keyboard
operation, unchanged drafts and empty input values. Screenshots and results are
written to `artifacts/guidance/`. The save-visibility check above separately verifies
real writes with a disposable account.
