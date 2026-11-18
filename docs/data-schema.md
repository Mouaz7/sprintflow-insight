# Data schema reference

PostgreSQL is the system of record. The authoritative, executable schema is the ordered Flyway migration set in `backend/src/main/resources/db/migration`; do not edit a deployed schema manually. The migration integration test applies all migrations to a clean PostgreSQL-compatible test database and starts Hibernate in `validate` mode.

## Logical relationships

- `users` stores normalized unique email addresses, BCrypt password hashes, profile fields, and hashed recovery codes.
- `projects` has one owner email and a unique `key` (2–10 upper-case letters, check constraint `projects_key_format`, unique `projects_key_unique`) that prefixes task identifiers such as `SF-123`.
- `project_member` stores project membership and the supported roles `Owner`, `Admin`, `Member`, and `Viewer`. Since V15 every row references its account through `user_id` (`users.id`, `ON DELETE CASCADE`, unique per project). Access checks match on `user_id` only; `email` is a display copy that follows the account's address. The owner always has an `Owner` row, which is what grants owner access.
- `sprint` belongs to a project and stores lifecycle state, planned points, schedule dates, and completion time.
- `user_stories` belongs to a project and can be assigned to one sprint in that same project. Story points are restricted to 1, 2, 3, 5, or 8; business value is optional and ranges from 1 to 5.
- `task` has a per-project `number` (unique with `project_id`, positive, never changed after creation). `project_task_counter(project_id, last_number)` holds the last number handed out; creation increments it with one `INSERT … ON CONFLICT DO UPDATE … RETURNING`, which locks the counter row, so concurrent creators never collide and numbers are never reused.
- `task` belongs to a project and can reference a same-project story and sprint and an assigned project member. Normal deletion preserves references in the recycle bin. Permanently purging a story clears its task references; permanently purging a task removes its associated comments and attachments.
- `task_status_history`, `activity_event`, `task_comment`, `task_attachment`, and `notification` are project-scoped supporting records. Status history stores actor and timestamp for workflow changes and state snapshots when points or sprint assignments change.
- `poker_session` estimates one story through a composite story/project foreign key; `poker_vote` identifies one user’s card per session and round. Only revealed rounds may leave the server, apart from the caller’s own card. One open session per story is enforced by a partial unique index. Story/project deletion cascades; account deletion removes its votes and nulls the facilitator. V23 adds these tables.
- `password_reset_tokens` references its account and stores only a SHA-256 hash of the one-time token.
- Migration V6 adds archive/trash timestamps, JSON-encoded task labels/checklists, versioned comments, account preferences, sprint retrospective entries and shared report snapshots.
- `user_preferences` references its account with cascading removal. Saved filters are private to that account.
- `retrospective_entry` is project/sprint scoped. Account removal anonymizes its author; permanent project removal deletes the entries.
- `shared_report` stores a hash of the random link token and a limited snapshot with creation/expiry times. Account removal revokes links created by that account; project removal deletes all project reports.

Migration V15 backfills the B0 building blocks without changing row versions. Project keys are derived from names (initials of up to four words, otherwise the first four letters, accents folded, `PRJ` when fewer than two letters remain); collisions get the suffix A, B, …, Z, AA in project-id order. Existing tasks are numbered in id order per project, and counters start at each project's highest number. Membership rows get `user_id` by case-insensitive email match. Rows without an account are removed, case-variant duplicates collapse to the most privileged row, a missing owner row is added, and stale `Owner` rows of former owners become `Member`.

The email match in V15 is ambiguous only if `users` holds two accounts whose addresses differ in case alone. `users.email` is unique only case-sensitively, but the application has always stored addresses lower-cased, so such pairs can only come from manual edits or imports. A member row could then attach to either account. V15 has already been applied, so it is left unchanged, and no guard migration was added, for two reasons. First, a check running after V15 cannot prevent the assignment; it could only block startup afterwards. Second, a V18 applied before the reserved V16/V17 would make Flyway reject those later migrations (out-of-order is disabled). Before upgrading a database that may contain such data, run this query; it must return no rows:

```sql
select lower(email), count(*) from users group by lower(email) having count(*) > 1;
```

If it returns rows, merge or rename the duplicate accounts before applying V15.

Encrypted columns (none yet; later phases add them) store `EncryptionService` envelopes `sf1:<keyId>:<base64url(iv|ciphertext|tag)>` in a text column. The key id makes key rotation possible; see [ADR 0015](adr/0015-encryption-and-key-rotation.md).

Migration V13 adds the Spring Session tables `spring_session` and `spring_session_attributes`. `principal_name` holds `user:<id>`, which lets the server find and revoke all of an account's sessions.

Migration V12 adds `user_stories.backlog_position` and a project/order index. Existing
stories keep their relative ID order; subsequent manual changes are atomic and versioned.
The backend owns the position field, and new stories append to their project's order.

## Isolation and lifecycle

Every project read and write checks membership in the service layer. PostgreSQL migrations keep indexes and schema changes reproducible. Normal UI deletion now moves work into the recycle bin; dedicated restore routes retain relationships and data. Archived projects remain readable but reject work changes. Trashed projects reject ordinary access. The owner-only DELETE project endpoint remains the permanent purge operation: it removes all project records in one transaction, including retrospective entries and reports. Uploaded attachment files are removed after commit, so a rollback preserves them. Other projects are unaffected. Account deletion is rejected while the account owns a project, including projects in the recycle bin; the owner must permanently remove the project or transfer ownership first. Account deletion removes account-linked tokens, comments, notifications, memberships and preferences, clears task assignees, anonymizes retrospective authors, revokes account-created reports and removes the account.

See [`architecture.md`](architecture.md) for the service boundaries and [`openapi.yaml`](openapi.yaml) for the REST surface.


B3 uses the V17 epic/story relation, task_dependency and task_estimate foundation delivered with B9. V20 adds task_estimate.version (initially 0) and active (initially true) for If-Match updates. Clearing an estimate retains an inactive revision marker, excluded from API reads and forecasts; replacement revisions increase instead of resetting. Permanent task deletion cascades to this marker. Cross-project relations are prevented by composite foreign keys; deleting an epic detaches its stories. Applied V17–V19 migrations are unchanged.

B5 adds V21: `history_project_time_idx` on `task_status_history(project_id,changed_at)`; `project_wip_limit(project_id,status,wip_limit)` and `project_wip_settings(project_id,version)`. Both new tables cascade on project deletion. The settings revision survives clearing limits and is checked using If-Match under the project row lock. Existing workflow history is not rewritten.
