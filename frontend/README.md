# Frontend

React/TypeScript with Vite. See the [main guide](../README.md) for the complete Docker start and configuration.

For development, use Node 24 and start the backend with `start.cmd -Native` from the repository root. In this directory:

```sh
npm ci
npm run dev
npm run check
```

The development server proxies API requests to Spring Boot on port 8081. `npm run check` runs tests and builds `dist`, which Maven includes in the server package. Do not commit `node_modules` or `dist`.

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

### Save visibility verification

Run `npm run creation-visibility --prefix scripts` from the repository root with the
local app running. `SPRINTFLOW_QA_URL` and `PLAYWRIGHT_EXECUTABLE_PATH` work as above.
This check uses the real backend/database: it creates a disposable account and two
projects, verifies project/story/sprint/task creation, choosing destination projects
inside the forms, preservation of draft text, clearing old project relationships,
visibility despite filters and persistence after reload, then removes only its own
test projects and account.
Screenshots are written to `artifacts/creation-visibility/`.

### Examples and guidance

All eleven workspace pages include keyboard-operable help. Creation/editing forms
show contextual examples in empty fields and expandable explanations in Swedish
and English. Examples are placeholders, never prefilled or saved data.

Run `npm run guidance --prefix scripts` against the local app. This uses API fixtures
to check expanded help at mobile, tablet, desktop and landscape sizes, keyboard
operation, unchanged drafts and empty input values. Screenshots and results are
written to `artifacts/guidance/`. The save-visibility check above separately verifies
real writes with a disposable account.
