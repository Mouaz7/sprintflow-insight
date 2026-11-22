# ADR 0041 — Offline workspace

Status accepted.

## Shell and cache

Vite PWA generates the installable manifest, icon set and service worker. The worker preloads the public shell and built files. Same-origin navigation tries the network and falls back to the packaged shell on connection error. API and WebSocket requests, public report links and recovery links bypass the navigation fallback. Cached HTML retains its own CSP header and matching nonce. The named Trusted Types policy accepts only the worker path `/sw.js`.

The first page renders before worker registration. The initial JavaScript budget remains 125 kB gzip. The tasks route budget is 20.8 kB and the complete application route budget is 426 kB to include the offline queue and persistence.

IndexedDB stores successful queries for recently opened boards, comments and time views under the last verified account. The client can open this snapshot for 24 hours from the last successful verification. A server denial never activates the offline fallback. Account changes and logout replace or clear the local data. Protected response bodies never enter the service worker cache. The browser installation and IndexedDB belong to the current origin and browser profile.

## Queued commands

Moves, new comments and manual time entries use a dedicated replay endpoint. Commands keep a UUID and the task version the user originally read. The server confirms the account, project write role and current task revision before applying the command. The operation receipt and domain change commit in one transaction under the project write lock. A retry can retrieve the original result with the same operation ID and payload. The next queued command for the same task advances only with the revision returned by its own previously queued command.

A 412 response holds that task's later operations until the user chooses to keep the server version or explicitly retry the change against the newly fetched revision. HTTP 409 and permission or validation failures are visible in the queue. Other tasks can sync independently. The browser caps the pending command list at 100 entries and uses a Web Lock for cross-tab replay when available.

An already active Yjs document can retain its state and pending update IDs in IndexedDB. On reconnect the editor confirms the current account and uses the existing WebSocket and CRDT projection flow. Local and remote Yjs updates merge even after the editor tab was reloaded. A document must be activated and opened while connected before it can be edited offline.

| Threat | Control |
| --- | --- |
| Spoofing | Signed-in session, CSRF and account binding on replay. |
| Tampering | Typed command payload, version precondition, hash and atomic result receipt. |
| Repudiation | Existing domain audit and activity events record the authenticated server action. |
| Information disclosure | Scoped IndexedDB data is cleared on logout or account replacement. The worker has only static assets and a public shell. |
| Denial of service | Bounded command queue and reused input limits. Replay serializes conflicting task writes. |
| Elevation of privilege | Project write checks apply again after acquiring the lock. Archived projects and read-only roles cannot replay writes. |

[REST contract](../openapi.yaml) · [Testing](../testing.md).
