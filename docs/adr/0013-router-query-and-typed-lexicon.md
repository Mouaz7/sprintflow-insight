# ADR 0013: Client routes, query cache and typed lexicon

Status: accepted for A6. Supersedes the `react-hooks/set-state-in-effect` exception in [ADR 0008](0008-frontend-feature-folders-and-lint.md).

## Context

Before A6 the React client had one URL. The current view lived in component state (restored from `sessionStorage`), shared reports and password resets used query parameters on `/` (`?report=`, `?resetToken=`), and a reload or a copied link always started at the overview. Every view fetched its own data in effects with manual `live`/`cancelled` guards (24 of them), and several components requested the same role, sprint, task or preference list at the same time. Language and theme were held separately by the workspace and the sign-in page, and the user interface text was written as about 475 `sv ? … : …` ternaries spread over 40 files. A missing translation was only visible at runtime.

## Decision

### Routing: React Router (declarative mode)

- `react-router` 7 with `BrowserRouter` and `<Routes>`; no data routers or framework mode, because the API is a separate REST service and loading is handled by the query cache.
- One route per workspace view (`/overview`, `/projects`, `/backlog`, `/sprints`, `/tasks`, `/metrics`, `/updates`, `/profile`, `/calendar`, `/retrospective`, `/trash`), `/report/:token` (public, no session request) and `/reset-password?token=…`. `/` opens the view remembered for the account in this tab; unknown paths go to `/overview`.
- Old links keep working: `/?report=x` redirects to `/report/x` and `/?resetToken=x` to `/reset-password?token=x`. The backend now creates the new paths, and `CspResources` forwards each client route to the nonce-transformed `index.html`, so a reload of any route gets the same CSP and `Referrer-Policy` as `/` (`strict-origin-when-cross-origin`; tokens never reach other origins). The route list in `CspResources.SPA_ROUTES` must change together with the router.
- Navigation entries are links (`NavLink`, `aria-current="page"`). After a client-side navigation focus moves to the page `<h1>`; on the first load it stays at the top of the document.
- Views are `React.lazy` chunks behind `Suspense`, and the sign-in page is a chunk of its own. size-limit checks the entry chunk, the two largest route chunks and all JavaScript together.

### Server state: TanStack Query

- `@tanstack/react-query` 5. Shared query options (session, projects, preferences, role, members, sprints, stories, tasks) are in `src/api/queries.ts`; feature-only queries are in `features/<name>/queries.ts`.
- Project data keys start with `['project', id]`. A write refreshes the keys of its own view and marks the rest of the project stale (`afterProjectWrite`), so other views reload when they open. The defaults keep the old behaviour: no retries, no refetch on focus or reconnect, and a short stale time (5 s) that only merges requests from components mounting together.
- Signing in or out removes the previous account's cached data. `react-hooks/set-state-in-effect` is enforced again; per-request UI state is derived during render.

### Text: a typed lexicon with precompiled ICU messages

- Each feature declares `defineLexicon({ EN: {...}, SV: {...} })` in `features/<name>/i18n.ts` (shared words in `shared/i18n/common.ts`, shell text in `app/i18n.ts`). Components call `useT(lexicon)`; `LanguageProvider` holds the language, persists it and sets `<html lang>`.
- The Swedish half is typed from the English half, so a missing key, an unknown key or a wrong argument fails `tsc`. A test uses `@ts-expect-error` to keep that guarantee honest.
- Entries are strings or functions of typed arguments and a formatter. Plural and date messages use genuine ICU MessageFormat patterns in `shared/i18n/icu.messages.json`, compiled by `@messageformat/core` into a static TypeScript module. `icu.ts` exposes typed message keys and arguments; `icu.types.ts` describes the compiled functions, and the generated module is type-checked against them. Only the small `@messageformat/runtime` helpers reach the browser, with no runtime parser, `eval` or `Function` constructor, so the existing CSP remains enforced. Locale rules and date skeletons use `en-GB`/`sv-SE`.
- After changing ICU patterns, run `npm run messages:compile`; keep the argument declarations in sync. `npm run messages:check` rejects stale compiled output during both `check` and `build`. Tests cover exact-zero and plural branches, localized counts and date skeletons. Other typed messages can use the shared `Intl.NumberFormat`/`Intl.DateTimeFormat` formatter directly.
- Server error messages keep their translation table (`localizeMessage`), and the page and form guides keep their existing typed `[sv, en]` copy.

### Theme and pre-render appearance

`ThemeProvider` holds the theme for both the sign-in page and the workspace and writes `data-theme` and the `theme-color` meta tag. `src/boot.ts` is the first module of the entry chunk referenced by `index.html`: it applies the remembered language and theme to `<html>` before React renders. It is an external module that receives the existing CSP nonce through Vite's `html.cspNonce`, so no inline script, `unsafe-inline` or `innerHTML` is needed, and Trusted Types enforcement is unchanged.

### Large lists: TanStack Virtual

Board columns and the backlog table switch to `@tanstack/react-virtual` above 60 items; shorter lists render exactly as before. The virtualized regions are focusable scroll containers with a label, and the table keeps `aria-rowcount`/`aria-rowindex`. Home/End on the viewport target the first/last list index through the virtualizer, so the browser cannot stop at the last currently mounted item. Keys inside child controls retain their normal behaviour. The React Compiler cannot memoize the virtualizer, so those two components opt out with `'use no memo'`.

### Tests

Vitest talks to the REST client through an MSW server (`src/test/server.ts`, `mockApi`) instead of replacing `fetch`. Component tests that mock the `api` module stay as they are. `renderWithProviders` supplies the query client, language, theme and a memory router. axe-core runs in the App-level accessibility tests and fails on serious or critical WCAG 2.0–2.2 A/AA findings.

## Consequences

- Every view has a URL that survives reload and can be shared; old report and reset links still work.
- Adding a view means adding a route in `WorkspaceRoutes.tsx`, its path in `CspResources.SPA_ROUTES`, the navigation label and a size budget if it is large.
- New text cannot be added to only one language. Plural and date formatting follow CLDR rules instead of hand-written variants. Counts of 1,000 or more now get a locale thousands separator.
- After a write, a list that is refetching keeps its old items visible instead of going blank, and the board's due dates use the selected language's date format instead of the browser default.
- Initial JavaScript fell from 141 kB to about 117 kB gzip. All route chunks together are about 203 kB, because the router, query cache and virtualizer add about 25 kB and split chunks compress less well than one bundle.
- jsdom has no layout, so contrast, target size and real scrolling are covered by the Playwright responsive and guidance checks against built assets, not by the unit-level axe tests.
