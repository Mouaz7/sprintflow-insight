# Backend

Java 25/Spring Boot REST API with PostgreSQL, Flyway and Spring Security server sessions. See the [main guide](../README.md) for Docker and the [API contract](../docs/openapi.yaml).

On Windows, run `start.cmd -Native` from the repository root to import `.env`, build the frontend, and start the backend. Maven alone does not import `.env`.

```powershell
.\mvnw.cmd verify
```

On Linux/macOS use `./mvnw clean verify` with Java 25 and Docker running. Testcontainers creates isolated PostgreSQL 16 databases automatically; tests do not use `.env` or an external test database. Build `frontend/dist` first to include the UI in the packaged application. See [testing](../docs/testing.md) for JaCoCo, PIT, jqwik and Toxiproxy commands.

Existing Flyway migrations must not be edited after deployment. The explicitly documented A2 conversion of V8 from Java to SQL requires a one-time history repair on existing databases before upgrading; follow [ADR 0005](../docs/adr/0005-postgresql-tests-and-sql-migrations.md). Local uploaded files are in `data/attachments`; Docker mounts the same `backend/data` directory.
