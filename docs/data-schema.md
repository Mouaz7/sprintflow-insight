# Data schema reference

PostgreSQL is the system of record. The authoritative, executable schema is the ordered Flyway migration set in `backend/src/main/resources/db/migration`; do not edit a deployed schema manually. The migration integration test applies all migrations to a clean PostgreSQL-compatible test database and starts Hibernate in `validate` mode.

## Logical relationships

- `users` stores normalized unique email addresses, BCrypt password hashes, profile fields, and hashed recovery codes.
- `projects` has one owner email. `project_member` stores project membership and the supported roles `Owner`, `Admin`, `Member`, and `Viewer`; the current owner is also represented in membership data.
- `sprint` belongs to a project and stores lifecycle state, planned points, schedule dates, and completion time.
- `user_stories` belongs to a project and can be assigned to one sprint in that same project. Story points are restricted to 1, 2, 3, 5, or 8; business value is optional and ranges from 1 to 5.
- `task` belongs to a project and can reference a same-project story and sprint and an assigned project member. Normal deletion preserves references in the recycle bin. Permanently purging a story clears its task references; permanently purging a task removes its associated comments and attachments.
- `task_status_history`, `activity_event`, `task_comment`, `task_attachment`, and `notification` are project-scoped supporting records. Status history stores actor and timestamp for workflow changes and state snapshots when points or sprint assignments change.
- `password_reset_tokens` references its account and stores only a SHA-256 hash of the one-time token.
- Migration V6 adds archive/trash timestamps, JSON-encoded task labels/checklists, versioned comments, account preferences, sprint retrospective entries and shared report snapshots.
- `user_preferences` references its account with cascading removal. Saved filters are private to that account.
- `retrospective_entry` is project/sprint scoped. Account removal anonymizes its author; permanent project removal deletes the entries.
- `shared_report` stores a hash of the random link token and a limited snapshot with creation/expiry times. Account removal revokes links created by that account; project removal deletes all project reports.

Migration V13 adds the Spring Session tables `spring_session` and `spring_session_attributes`. `principal_name` holds `user:<id>`, which lets the server find and revoke all of an account's sessions.

Migration V12 adds `user_stories.backlog_position` and a project/order index. Existing
stories keep their relative ID order; subsequent manual changes are atomic and versioned.
The backend owns the position field, and new stories append to their project's order.

## Isolation and lifecycle

Every project read and write checks membership in the service layer. PostgreSQL migrations keep indexes and schema changes reproducible. Normal UI deletion now moves work into the recycle bin; dedicated restore routes retain relationships and data. Archived projects remain readable but reject work changes. Trashed projects reject ordinary access. The owner-only DELETE project endpoint remains the permanent purge operation: it removes all project records in one transaction, including retrospective entries and reports. Uploaded attachment files are removed after commit, so a rollback preserves them. Other projects are unaffected. Account deletion is rejected while the account owns a project, including projects in the recycle bin; the owner must permanently remove the project or transfer ownership first. Account deletion removes account-linked tokens, comments, notifications, memberships and preferences, clears task assignees, anonymizes retrospective authors, revokes account-created reports and removes the account.

See [`architecture.md`](architecture.md) for the service boundaries and [`openapi.yaml`](openapi.yaml) for the REST surface.
