# ADR 0008: Frontend feature folders, import boundaries and lint gate

Status: accepted for A5.

## Context

The React client had grown in one flat folder. Several files mixed data loading, state transitions and rendering (`TasksView` about 900 lines, plus `MetricsView`, `BacklogView`, `ProfileView` and one large `App.test.tsx`). Nothing stopped one view from importing another view's internals, and `npm run check` ran only Vitest and the Vite build. Later phases add views per feature, so structure and checks are fixed before that growth.

## Decision

`frontend/src` has four layers:

| Folder | Contents | May import |
| --- | --- | --- |
| `app/` | Application shell, workspace routing, cross-feature panels and App-level tests | `app`, `features/*`, `shared/*`, `api` |
| `features/<name>/` | `auth`, `tasks`, `planning`, `insights`, `projects`, `profile`, `onboarding` | its own feature, `shared/*`, `api` |
| `shared/{ui,i18n,lib}` | Feature-neutral components, translations and hooks/helpers | `shared/*`, `api` |
| `api/` | REST client (CSRF, credentials) and contracts | `api` |

A feature never imports another feature or `app`. When two features need the same code it moves to `shared`; composition across features happens in `app`. `eslint-plugin-boundaries` enforces the table (`boundaries/dependencies`, default disallow), and `scripts/check-boundaries.ts` lints probe imports so a misconfigured rule that silently allows everything fails `npm run lint`.

Large views are split without behaviour changes: data and state live in a hook (`useTaskBoard`, `useBacklog`, `useProfile`), rendering in focused components (`Board`, `TaskEditor`, `TaskFilters`; `StoryTable`, `StoryEditor`; `SprintAnalysis`, `FlowAnalysis`, `MetricsHistory`; `ProfileDetails`). App-level tests are grouped per area (`App.auth`, `App.tasks`, `App.planning`, ...) with shared fixtures in `appTestFixtures.ts`.

Tooling:

- ESLint 9 flat config in TypeScript (`eslint.config.ts`): `typescript-eslint` **strict**, `react-hooks` recommended-latest (including the React Compiler rules), `jsx-a11y` recommended and the boundary rule; `--max-warnings 0`. Non-null assertions and `any` are allowed only in tests. `react-hooks/set-state-in-effect` is off because views still reset loading/error state before effect fetches; replacing those fetches with query hooks will make it enforceable.
- Prettier formats everything except generated output and the lockfile.
- `tsconfig.json` adds `noUncheckedIndexedAccess` and `exactOptionalPropertyTypes`.
- React 19 with the React Compiler (`babel-plugin-react-compiler` through `@rolldown/plugin-babel` and `reactCompilerPreset`). Components the compiler cannot prove safe are left uncompiled by the compiler itself.
- `npm run check` = `lint` (ESLint and boundary probe) → `format:check` → `typecheck` → `test` → `build` → `size` (size-limit: 150 kB gzip JavaScript, 15 kB gzip CSS).
- Build-only packages, including `@vitejs/plugin-react`, are `devDependencies`; the shipped bundle depends only on `react` and `react-dom`.

The repository check forbids tracked `.js`/`.mjs` files, so the ESLint config and the boundary probe are TypeScript (`jiti` loads the config; Node 22.18+/24 strips types for the probe).

## Consequences

- New feature code goes to `features/<name>/`; a cross-feature import fails CI instead of a review comment.
- The stricter compiler options require explicit handling of indexed lookups and optional properties, which surfaced a few non-null assertions that were replaced by guards.
- The size limits leave about 9 kB JavaScript headroom; exceeding them is a reviewed decision, not a silent drift.
- The compiler adds a Babel pass to builds and tests (a few seconds).
