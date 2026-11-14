# 0002 – Sessions in PostgreSQL via Spring Session JDBC

**Status:** Accepted (phase A1)

## Context
Sessions lived in Tomcat memory. A password change or reset left every other session signed in, which matters when the change was made *because* a device or password was compromised. In-memory sessions are also lost on restart, and they block running more than one instance (phase C6).

## Decision
- Store sessions with Spring Session JDBC (Flyway V13; `initialize-schema=never`).
- Index each session by `user:<id>`, set explicitly at sign-in, instead of the email principal, so revocation survives email changes.
- `UserSessions` handles credential changes. A password change rotates the caller's session id and deletes the account's other sessions. A reset by link or recovery code, or deleting the account, deletes all of them.
- The cookie is `SPRINTFLOW_SESSION` (HttpOnly, SameSite=Lax, Secure by default).

## Consequences
- One extra read and write per request to `spring_session`. This is negligible next to the API's own queries and is indexed by primary key.
- Sessions survive deployments, and any instance can serve any user.
- During rotation the store still lists the session under its old id until the request commits, so `UserSessions` excludes both ids when revoking. `AccountSecurityIntegrationTest` covers this.
- Expired sessions are removed by Spring Session's cleanup job every five minutes.
