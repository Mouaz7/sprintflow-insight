# Backend

Java 17/Spring Boot REST API with PostgreSQL, Flyway and Spring Security server sessions. See the [main guide](../README.md) for Docker and the [API contract](../docs/openapi.yaml).

On Windows, run `start.cmd -Native` from the repository root to import `.env`, build the frontend, and start the backend. Maven alone does not import `.env`.

```powershell
.\mvnw.cmd verify
```

On Linux/macOS use `./mvnw verify`. Build `frontend/dist` first to include the UI in the packaged application. Tests use their test database configuration. Existing Flyway migrations must not be edited after deployment. Local uploaded files are in `data/attachments`; Docker mounts the same `backend/data` directory.
