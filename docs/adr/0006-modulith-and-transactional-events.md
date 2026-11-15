# ADR 0006: Feature services and durable module events

Status: accepted for A3. Depends on ADR 0005's PostgreSQL test baseline.

## Decision

Keep a modular monolith with five application modules:

| Module | Responsibility | Dependencies |
| --- | --- | --- |
| `common` | Problems, tokens and event-publication maintenance | Framework APIs |
| `auth` | Accounts, credentials, preferences, sessions and security filters | Common |
| `workspace` | Projects, membership, stories, tasks, sprints, activity, comments, attachments and lifecycle | Auth and common |
| `reporting` | Metrics, CSV/PDF exports and expiring shared snapshots | Workspace read interfaces, identity events and common |
| `notifications` | Inbox, reminders and durable event consumers | Workspace read interfaces/events and identity |

Workspace children are feature packages within one module. Their existing cross-aggregate invariants (project locking, story/sprint membership, task history and atomic deletion) share a transaction; treating each as an independent module would introduce cycles or weaken these invariants. Named interfaces expose the existing project/task/sprint/story/activity read models and events to reporting and notifications. These interfaces include Spring Data repositories used by the existing read paths; they are declared dependencies, not a claim of independent persistence ownership. Controllers can only call services; an architecture test rejects repository dependencies. Further read-model isolation can replace these interfaces without changing HTTP contracts.

The first A3 commit contains only package moves/imports/formatting. Application services then take over the existing controller operations. HTTP addresses, validation, CSRF/session handling, role checks, optimistic versions and response shapes remain compatible. Existing transport types (`ResponseEntity`, servlet export streams and session parameters) remain at this application-service boundary to keep this refactor separate from an API redesign.

`ApplicationModules.verify()` checks cycles, allowed dependencies and exposed interfaces. Additional architecture rules reject controller-to-repository references and field injection. `ModuleArchitectureTest` generates C4 PlantUML diagrams and module canvases under `docs/architecture/`.

Use Spring Modulith **1.4.14**, the line [compiled against Boot 3.5](https://docs.spring.io/spring-modulith/reference/1.4/appendix.html). The next Boot 4 phase must upgrade Modulith as a compatible pair.

## Event semantics

- `TaskMoved`, `CommentAdded` and `NotificationRequested` carry immutable facts, a UUID and a timestamp, never a managed entity or credentials.
- Publication happens inside the domain transaction. Modulith's **JDBC Event Publication Registry** writes `event_publication` through the same data source/transaction manager. Flyway V14 creates the registry; automatic schema initialization stays disabled and Hibernate stays on `validate`.
- Notification consumers use `@ApplicationModuleListener`: asynchronous **AFTER_COMMIT**, in a separate transaction. A rolled-back task/project operation produces neither a durable publication nor a notification.
- Failed listener executions remain incomplete. Startup republishes outstanding entries, and `EventPublicationMaintenance` retries entries older than one minute every minute. Completed entries are deleted (`completion-mode=delete`), so successful deliveries do not retain payload copies indefinitely. Failed entries remain visible for diagnosis; monitor their count and oldest publication date. A poison event is retried until its cause is fixed; this phase does not provide a dead-letter UI or exactly-once delivery.
- Delivery is **at least once**. `notification(source_event_id, recipient_email)` has a unique index, and consumers check it before insertion. Replaying after a consumer commit does not create another inbox item. Concurrent replays can hit that index; the transaction rolls back and a later retry observes the existing delivery.
- Before durable delivery, verify the recipient account and project still exist. A pending event must not recreate inbox rows after account/project deletion. Other settings, such as notification preferences, are evaluated when delivered.
- `AccountDeleting`, `UserEmailChanged` and `ProjectDeleting` are deliberately **synchronous** events. Their listeners join the original transaction (`MANDATORY`); ownership validation, anonymization, reference updates and dependent-row deletion must succeed together. They do not enter the asynchronous registry. Account deletion cannot succeed while owned projects remain.
- Attachment cleanup retains its existing transactional deletion queue. This phase does not replace its filesystem retry mechanism.

## Operations and verification

Apply V14 with the normal application/Flyway startup. Upgrade from V13 needs no history repair. Earlier installations with the historical Java V8 migration still require the explicitly guarded repair procedure in ADR 0005 before migration.

```sql
select listener_id, event_type, count(*), min(publication_date)
from event_publication where completion_date is null
group by listener_id, event_type;
```

Inspect access-controlled logs for listener failures; avoid copying serialized payloads into public logs. Keep event class names/JSON schemas compatible while incomplete entries exist. Before renaming/removing an event, drain or explicitly migrate outstanding publications.

`ModuleEventsIntegrationTest` uses real PostgreSQL and Scenario to verify committed task movement, comment recipient deduplication, rollback of domain data and registry, a failed listener, retry, and replay after a successful consumer commit. The existing HTTP suites retain authorization/API coverage. Browser checks exercise the packaged application and its persistent data paths.
