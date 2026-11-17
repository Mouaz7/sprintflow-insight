# ADR 0017: Realtime updates over STOMP with per-project sequence numbers

Status: accepted for B1. C6 updates the transport section when it introduces the broker relay.

## Context

Boards, comments and sprint state should update for everyone viewing a project without a manual reload. The application already publishes typed domain events through Modulith's durable JDBC registry (ADR 0006), authenticates with server-side Spring Session JDBC sessions plus CSRF (ADR 0002), and authorises every project operation through `ProjectAccessService` (ADR 0004). A push channel must reuse those mechanisms rather than add a second identity or permission model. It must also tolerate dropped connections: a client that was offline for a while must learn what it missed, or at least that it missed something.

## Decision

### Transport

- **STOMP over a plain WebSocket at `/ws`** (`spring-boot-starter-websocket`, Spring Security messaging). No SockJS fallback: every supported browser has WebSockets, and the fallback would add HTTP streaming endpoints to secure.
- **One instance now: Spring's in-memory simple broker** for `/topic`. It needs no infrastructure and is enough for a single application instance.
- **Several instances later (C6): STOMP broker relay to RabbitMQ.** The simple broker only reaches clients connected to the same JVM. C6 replaces `enableSimpleBroker` with `enableStompBrokerRelay`, keeps the topic names and moves presence to the relay (or to a shared store). The sequence numbers below already live in PostgreSQL, so they stay correct with any number of instances.

### Authentication and authorisation

- The handshake is an ordinary HTTP request through the security filter chain. `/ws` requires an authenticated session cookie, so there is no token in the URL and no separate login. The browser `Origin` must either match the request's own host (same origin) or appear in `app.realtime.allowed-origins` (default: the public URL, `SPRINTFLOW_PUBLIC_URL`). Requests without an `Origin` header (non-browser clients) still need the session cookie.
- `@EnableWebSocketSecurity` checks the session's CSRF token in the STOMP `CONNECT` headers (`X-CSRF-TOKEN`, the masked value from `GET /api/auth/csrf`). A cross-site page cannot read that token, so it cannot open a usable connection with the victim's cookie.
- Frame rules: frames without a destination (CONNECT, DISCONNECT, UNSUBSCRIBE, heartbeats) need the authenticated session user. `SUBSCRIBE` is allowed only to `/topic/projects/{id}` and `/topic/projects/{id}/presence`, and only when `ProjectAccessService.canRead` grants READ (any member, archived projects included, never the recycle bin). The only allowed `SEND` is `/app/projects/{id}/presence` with a validated positive `taskId` or null. It requires READ, an existing events subscription and a non-trashed task in that project. It cannot mutate domain data. All other subscriptions and SEND destinations are denied and logged on `security.events`.
- Inbound processing uses `SyncTaskExecutor` on each WebSocket transport thread; different connections remain concurrent. This preserves that socket's frame order and lets authorization errors reach the STOMP handler. Spring 7.0.10's `OrderedMessageChannelDecorator` catches channel errors and returns true even for rejected frames, suppressing ERROR/close and allowing a false subscribe event. We therefore disable that receive decorator, while retaining ordered outbound publication. Real socket regressions require both the ERROR and the absence of an outsider in presence.
- A connection outlives the request that authenticated it, and Spring Session JDBC publishes no deletion events. Every 30 seconds a guard therefore checks each connection's HTTP session (logout, password change, revocation and expiry all delete it) and re-checks READ for each subscribed project. Failing connections are closed with status 1008 and must reconnect through a new handshake. The window for a revoked user is at most one sweep, and messages carry no content (see below).
- Limits: 16 KiB per inbound frame, 16 subscriptions per connection, 30 seconds to the first frame, a 512 KiB send buffer per connection and 10-second heartbeats.

### Message production

`RealtimeEventListener` consumes the durable events with `@ApplicationModuleListener`: `TaskCreated`, `TaskMoved`, `TaskUpdated`, `TaskDeleted` (moved to the recycle bin), `CommentAdded` and `SprintStatusChanged`. These listeners run only after the domain transaction commits, so a rolled-back change never reaches a client. In its own transaction the listener allocates the next sequence number, writes the replay row, and registers the broker send as an `afterCommit` callback. A failing listener stays in the registry and is retried (ADR 0006).

`WorkspaceChanged` covers aggregate activity (including story/sprint cascades and project edits), plus comment edits/deletions. `NotificationDelivered` is emitted after notification storage in its transaction, so `NotificationsChanged` cannot race ahead of persistence. All use the durable publication registry and the same replay log.

A message is `{projectId, seq, type, entityId, version}`. It names what changed, never titles, comments or user data. The client reloads through the REST API, which applies the normal authorisation. `entityId` is the task or sprint; for `CommentAdded` it is the commented task. `version` is the optimistic-lock version after the change. Services flush before publishing so the version is the one the client will see. It is `null` for deletions and comments.

### Sequence numbers and gap filling

- `realtime_sequence(project_id, last_seq)` holds the counter (Flyway V19). `INSERT … ON CONFLICT DO UPDATE SET last_seq = last_seq + 1 RETURNING last_seq` increments it under a row lock. Allocations for one project are therefore serialised and commit in sequence order. A listener that rolls back also rolls back its number, so the numbers have no holes. Different projects do not contend.
- `realtime_event` is the replay log, keyed by `(project_id, seq)`, with a unique `source_event_id`. A replayed module event (at-least-once delivery) is recognised and gets no second number. A concurrent replay loses on the unique index and rolls back with its number.
- Clients track the last `seq` they applied. A duplicate (`seq ≤ last`) is ignored. A jump (`seq > last + 1`), or any reconnect, triggers `GET /api/projects/{id}/events?after=last` (READ access). The answer contains the missing messages in order, or `resync: true` with `latestSeq`. Resync means the range is no longer complete or is longer than one page (200). The client then reloads the project and continues from `latestSeq`.
- **Retention:** rows older than `app.realtime.retention` (7 days) are removed every hour, and each project keeps at most `app.realtime.max-events-per-project` (1000) rows. The counter is never reset. Deleting a project cascades to both tables, and events for a deleted project are dropped.

### Presence

Presence is kept in memory per instance. A connection counts as viewing a project while it subscribes to the project's events topic. Join and leave follow STOMP subscribe, unsubscribe and disconnect events. The full viewer list (`{userId, name, taskIds}`, never an email) is broadcast to `/topic/projects/{id}/presence` on every change. Open-task ids are combined across a user's tabs and cleared on close/unsubscribe/disconnect. The guard confirms live connections on each sweep, and entries not confirmed within `app.realtime.presence-ttl` (90 s) are dropped, so a lost disconnect cannot leave a ghost viewer. With several instances each instance only sees its own connections; C6 moves presence to the relay.

### Browser and CSP

The CSP keeps `connect-src 'self'`. The browser E2E flow verifies actual same-origin WebSocket connections under the enforced policy and asserts no CSP or Trusted Types violations. The client is a small hand-written STOMP 1.2 implementation in `frontend/src/features/realtime/`, used instead of `@stomp/stompjs` to keep the bundle small. It reconnects with exponential backoff (1 s doubling to 30 s, with jitter).

## Consequences

- One more table pair and a few short transactions per domain event. The counter row serialises realtime production within one project, never the domain transaction itself.
- Broker beans make Boot's scheduling and async-executor auto-configuration back off. `WebSocketConfiguration` restores the virtual `taskScheduler` and a named `applicationTaskExecutor`/`taskExecutor`. Four virtual listener workers retain the shipped concurrency limit; a bounded queue of 1,000 tasks allows nested durable publications without blocking all publisher workers. Saturation rejects further submissions; their durable registry entries remain available for the existing retry maintenance. `RealtimeExecutorIntegrationTest` verifies virtual workers, the database-pool margin and nested delivery in the full WebSocket context.
- Aggregate events invalidate the active project's queries after bulk changes; task and comment events invalidate the affected list/thread keys. Bursts are coalesced for 250 ms, notification events refresh the bell, and a resync also refreshes open task threads. Local editor and comment drafts remain component state and survive cache refreshes.
- Until C6, realtime and presence are correct only with a single application instance.

## Verification

`RealtimeWebSocketIntegrationTest` (real server, `WebSocketStompClient`) covers handshake rejection without a session or with a foreign Origin, CONNECT without CSRF, outsider subscriptions, denied SEND, delivery of `TaskMoved` with the next sequence number and the committed version, no message after rollback, gap filling, presence, and closing connections after logout or membership removal. `RealtimeEventsAccessIntegrationTest` checks the REST endpoint for every role, outsiders, archived and trashed projects. `RealtimeSequenceIntegrationTest` is a jqwik property: concurrent publishers, including simultaneous duplicate deliveries, get strictly increasing, contiguous numbers per project. It also covers retention and resync.

The inbound error regression was traced against the installed [Spring messaging 7.0.10 source artifact](https://repo.maven.apache.org/maven2/org/springframework/spring-messaging/7.0.10/spring-messaging-7.0.10-sources.jar), specifically `OrderedMessageChannelDecorator.sendNextMessage()`.
