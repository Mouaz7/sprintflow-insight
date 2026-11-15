# 0005 – PostgreSQL tests and SQL-only Flyway migrations

**Status:** Accepted (phase A2)

## Context

H2 emulated PostgreSQL but could not test its partial indexes, JDBC sessions or failure behaviour. V8 used Java to select different DDL for each engine. The integration suite mixed eight features in a single class. PostgreSQL is now the only supported engine, so V8 can be expressed directly in SQL as required by A2.

## Decision

- Replace H2 with one pinned `postgres:16.10-alpine` Testcontainers database per test JVM, shared through Spring Boot `@ServiceConnection`. Contexts share data, so fixtures use unique identifiers. Feature suites inherit `HttpFlowTest`; migration tests create and drop isolated schemas.
- Express V8 in SQL with the same conflict check and partial unique index as its PostgreSQL Java path. It refuses conflicting data without changing sprint statuses. No schema shape changes and no new migration version are needed.
- Require a deliberate, one-time history type update followed by Flyway repair for installations that already applied Java V8. Never enable automatic repair or disable validation at application startup.
- Test both fresh schemas and an actual V12 release history using the old Java V8 implementation. Assert that validation fails before repair, repair aligns only V8, and upgrading to the latest migration preserves existing data and the index. This fixture is the N-1 release baseline; update it when a new release becomes the previous release.
- Keep Hibernate `ddl-auto=validate`. Run coverage and bounded mutation checks in CI, property checks with jqwik, and isolated database fault tests through Toxiproxy.

## Existing installations: one-time V8 repair

Fresh databases need no repair. For a database that already applied Java V8:

1. Back up the database and stop application instances. Use this release's complete migration directory, Java 17 and Maven wrapper. Export `SPRINTFLOW_DB_URL` (a JDBC PostgreSQL URL), `SPRINTFLOW_DB_USER` and `SPRINTFLOW_DB_PASSWORD` for the intended database; Maven does not read `.env` automatically.
2. From `backend`, inspect `./mvnw flyway:info` and the history:

   ```sql
   select version, description, type, script, checksum, success
   from flyway_schema_history order by installed_rank;

   select indexdef from pg_indexes
   where schemaname = current_schema() and indexname = 'sprint_one_active_project_idx';
   ```

   Applied V8 must be the successful Java migration (`JDBC`, no checksum) and the index must be unique on `sprint(project_id)` with `status = 'Active'` and `trashed_at is null`. Investigate any other missing, failed or altered migrations first. In a non-default schema, configure `-Dflyway.schemas=your_schema` consistently for every command.
3. Run `./mvnw '-Dflyway.ignoreMigrationPatterns=*:pending' flyway:validate` (pending new migrations are expected before startup). For this upgrade the expected discrepancy is V8's type/checksum changing from Java to SQL. Review the difference against this release; repair changes history and does not recreate database objects.
4. With the application still stopped, change **only** the expected Java V8 history entry to SQL. Flyway 11.7.2 otherwise treats the old Java entry as missing and marks it deleted, so do not run `repair` before this step. In `psql` connected to the same database/schema:

   ```sql
   begin;
   update flyway_schema_history
   set type = 'SQL', script = 'V8__single_active_sprint.sql'
   where version = '8' and type = 'JDBC' and checksum is null and success;
   -- Expect UPDATE 1. If not, ROLLBACK and investigate instead of continuing.
   commit;
   ```

5. Run `./mvnw flyway:repair`, then `./mvnw '-Dflyway.ignoreMigrationPatterns=*:pending' flyway:validate`. Repair must align V8's checksum only, without removing failed migrations or marking missing migrations deleted. Recheck the history and index. V8 is now `SQL` with a checksum, and its existing index remains in place. A direct repair before step 4 is unsupported by this upgrade procedure.
6. Start the new application. Flyway applies pending migrations normally and Hibernate validates the resulting schema. Verify `/actuator/health` and an authenticated workflow. Do not repair again on routine restarts.

The Maven plugin uses the application's managed Flyway version and `filesystem:src/main/resources/db/migration` for all commands. Flyway [repair](https://documentation.red-gate.com/fd/repair-277578892.html) repairs schema history and must use the same migration locations as validation/migration. The guarded metadata update above is required by the Java-to-SQL resolution behaviour verified on the application’s managed Flyway 11.7.2; repair then aligns the SQL checksum. `SchemaMigrationTest` verifies this path against real PostgreSQL.

## Consequences

Docker is required for database tests. Testcontainers cleans up containers when the JVM exits. Toxiproxy uses a separate database/network so injected outages cannot affect other suites. An existing installation has an explicit upgrade step; new installations stay fully automatic. Future schema changes use the next versioned SQL migration rather than editing history.
