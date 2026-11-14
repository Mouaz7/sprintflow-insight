# Architecture

SprintFlow keeps the browser client and REST API separate during development and packages them together for production.

```text
frontend/   React + TypeScript + Vite
backend/    Java 17 + Spring Boot + REST API
Dockerfile  Node 24 + Maven/Java 17 build, non-root Java runtime
compose.yaml App + PostgreSQL 16, persistent volume and uploads
```

```text
React + TypeScript (Vite, port 5173)
        │ /api proxy in development
        ▼
Spring Boot REST API (port 8081)
        │ Spring Security session + CSRF
        ▼
PostgreSQL + Flyway migrations
```

## Boundaries

- `frontend/src/api` is the frontend boundary for REST requests. It sends the server-issued CSRF token on every state-changing request and always uses same-origin credentials. The Google sign-in component also loads the official Identity Services script.
- The Java API has feature packages for authentication, projects, stories, tasks, metrics, collaboration and notifications.
- Spring Security protects `/api/**` except narrowly scoped sign-in, registration, recovery, CSRF and expiring public-report reads.
- Sessions identify the account by immutable user ID. Profile changes lock that account and move email-based project, assignment, notification and author references together in one transaction. Existing sessions resolve the account's current email from its ID.
- Authentication POST routes have a one-minute limit per client IP and endpoint: 30 login/Google/token-confirmation attempts, 20 registrations and 10 recovery requests. Rejections return HTTP 429 with `Retry-After`. Counters are bounded, expire automatically and are local to each application process; restart resets them. Forwarded IP headers are not trusted by this filter.
- `ProjectAccessService` authorizes project reads and writes according to Owner, Admin, Member and Viewer roles; lifecycle rules also protect archived and trashed work.
- PostgreSQL is the production database. H2 exists only as an isolated test database.
- Project and notification visibility is filtered in database queries. Both list endpoints accept optional `page` and `size` parameters; existing clients can omit `page` to receive the complete accessible list. Due-date reminders query eligible tasks in batches of 200 and load project memberships in batches.

## Browser interface

`App` loads the server session and projects, or shows an anonymous report when a report token is present. `AuthScreen` handles sign-in, registration, reset requests and reset links. `Workspace` owns top navigation, language, theme and project selection. Dedicated pages render the dashboard, projects, backlog, sprints, tasks, metrics, activity timeline, profile, calendar, retrospectives and recycle bin. Shared components provide discussions, checklists, labels, saved filters, notifications, onboarding and report sharing.

`ui.tsx` provides page headings, feedback, side panels and confirmation dialogs. The dialog focus helper traps keyboard navigation, disables background interaction and restores focus. Dirty editors request confirmation before closing. Styling is defined once in `styles.css` and `design-system.css`, using shared theme and spacing variables.

The original redesign reused the existing REST routes and schema. The subsequent collaboration features add lifecycle, preference, template, retrospective and report routes, with migration V6 for durable storage. Normal UI deletion uses soft-delete timestamps; permanent purge remains a separate owner-only transaction. Report links store hashed tokens and aggregate snapshots. Account preferences are saved on the server; the browser also caches appearance and project selection.

## Local verification

The backend test suite covers session-protected API flows, roles, attachments, project-scoped metrics and CSRF/security headers. The React test suite covers UI flows and CSRF use in the REST client. CI runs both suites, builds the production React client, packages it into Spring Boot, verifies the frontend is present in the JAR, and stores the package as a seven-day workflow artifact. Automated deployment is not configured because this repository has no deployment target.

CI also runs the integration flows against an isolated PostgreSQL 16 service. To run these locally against a disposable test database, set `SPRINTFLOW_TEST_DB_URL`, `SPRINTFLOW_TEST_DB_DRIVER=org.postgresql.Driver`, `SPRINTFLOW_DB_USER` and `SPRINTFLOW_DB_PASSWORD`, then run Maven verification. Use a separate test database: the suite writes fixture accounts and projects.
