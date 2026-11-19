# ADR 0025: Personal project dashboards

Status: Accepted

B18 adds one personal dashboard layout per account and project. V25 follows B16 V22 (#70), planning poker V23 (#71) and time/capacity V24 (#72); merge and deploy those migrations first. The PR is stacked above #72.

The server stores only the ordered widget types and each card's wide/narrow choice. Six unique types are supported: burndown, cumulative flow, delivery forecast, WIP, my open tasks and sprint goal. An empty list is intentional. Reading an unsaved dashboard returns defaults and version zero without writing a row. Persisted revisions expose the JPA version plus one, so an unsaved default and the first saved row cannot share a concurrency token. PUT requires the revision the caller read, including the initial zero. A project lock serializes first creation; stale saves return 412 rather than overwriting another window.

Every endpoint checks ProjectAccessService and derives the account id from the session. All current members, including Viewers, can customize their personal layout because this changes no project work or shared settings. Archives can be read but cannot be customized; trash and outsiders cannot read. The database enforces one layout per project/account and bounded JSON arrays, with project and account deletion cascades. No other account's layout is addressable. These private preferences emit no project activity or realtime payload.

The `/dashboard` route reads existing metrics, flow, forecast, sprint and task APIs. Only mounted widgets request data, with shared Query keys deduplicating CFD and WIP. Chart rendering and accessible data tables reuse A7 components. Burndown and sprint goal follow the selected sprint (the active sprint by default); CFD/WIP cover the project over 30 days, forecast uses project throughput, and my tasks includes only the caller's unfinished assignments with 20 visible links. Each widget handles missing history and request failures independently. Layout failures hide cached widgets and editing controls.

Layout editing uses checkboxes, explicit up/down controls and a wide-card choice. The persisted order is also the DOM/keyboard order; on mobile all cards use a single column. No drag-only interaction is required. A failed save preserves the captured draft and revision. After a conflict the user explicitly closes/reopens the editor to load the current revision. Project switches reset local drafts and sprint query parameters; account sign-out uses the existing cache clearing.

The feature adds no dependency. The lazy route initially measures 8.35 kB gzip and all routes total 298.27 kB; the aggregate budget is 305 kB and the dashboard route has a 15 kB limit. Existing entry, other route, CSS and coverage gates remain unchanged.
