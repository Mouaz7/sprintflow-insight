# ADR 0007: Spring Boot 4 and Java 25

Status: accepted for A4. Supersedes the runtime/tool versions in ADR 0005 and the Modulith version in ADR 0006.

## Decision

Use **Spring Boot 4.0.9**, **Java 25**, and **Spring Modulith 2.0.9**. Boot's supported 4.0 line manages Spring Framework/Security 7, Jackson 3, Hibernate 7.2.25.Final and Flyway 11.14.1 together. Boot 4.1.2 was also stable when this change was made, but it manages Flyway 12.4.0; the A4 issue explicitly requires Flyway 11. Choose the supported 4.0 line instead of overriding its tested persistence versions. Revisit the whole version pair during a future platform upgrade.

Remove the application pins for Jackson, Tomcat, Log4j2 and PostgreSQL; use Boot's BOM. Import Google's API Client 2.9.2 and HTTP Client 2.2.0 BOMs for its clients. Jackson databind/core code moves to `tools.jackson`; Jackson's annotations correctly retain the `com.fasterxml.jackson.annotation` package. The converters continue reading the existing JSON columns, and all request/response contracts remain under the existing OpenAPI/HTTP tests.

Use Boot 4's technology-specific MVC, Flyway, session and client starters, new HTTP test autoconfiguration and Testcontainers 2 module names. Security 7's `DaoAuthenticationProvider` receives its `UserDetailsService` in the constructor. Existing Mockito replacements already use Framework `@MockitoBean`/`@MockitoSpyBean`.

The Docker builder now uses Java 25 and the checked-in Maven wrapper; the runtime uses a non-root Java 25 JRE. CI and local startup requirements use Java 25. The JDK flag `spring.threads.virtual.enabled=true` enables Boot's virtual request, async and scheduling executors, and `spring.main.keep-alive=true` keeps an app with daemon virtual threads alive.

## Existing data and durable events

No database shape changes are needed. Keep Hibernate on `ddl-auto=validate` and all existing Flyway migrations immutable. Modulith 2.0.9 supports its previous registry layout through `spring.modulith.events.jdbc.use-legacy-structure=true`; configure it to continue reading/retrying the V14 registry without dropping publications. Its SQL v1 schema matches V14. Scenario tests cover publication rollback, failed delivery and replay under the new runtime.

The old Java V8 history transition still needs the guarded, one-time procedure from ADR 0005, using **this release's Java 25 wrapper**. This platform change does not perform automatic history repair.

## Outbound HTTP

A shared JDK-backed `RestClient` uses a 3-second connect timeout and 5-second read timeout (configurable as `app.http.connect-timeout`/`app.http.read-timeout`). A small `GoogleRestClientTransport` adapter routes the Google verifier's signing-key GETs through that client. Google continues to perform signature, issuer, audience, expiry and verified-email checks; no token validation is reimplemented here. Certificate responses are limited to 1 MiB and redirects are not followed. Local HTTP tests cover cache headers, body preservation, non-success status, oversized documents and a real read timeout. Tests replace Google verification where appropriate; no real Google credentials or external mail are used.

## Security and evaluation

Remove the prior OSV exceptions rather than carrying applicability waivers forward to the new dependency graph. The scanner must pass without them. Any retained transitive override must name the actual advisory and reason.

`scripts/performance/workspace-read.k6.js` runs k6 2.3.0 against a disposable local application/database. It creates and deletes its own account/project, warms ten iterations, and runs five virtual users for twenty measured seconds. Each iteration reads projects, tasks and metrics and pauses 100 ms. `scripts/verification/summarize-performance.ts` extracts only measured requests into small committed summaries in `docs/evaluation/`; raw output stays in ignored artifacts. Both runs use a 23-task Scrum dataset, a 256–512 MiB app heap, two active processors and a 2-CPU/512-MiB PostgreSQL container. The summaries are RQ1 evidence for E1. One local sample per runtime, uncontrolled host load and caches cannot establish a general causal performance improvement.

## Sources

- [Boot 4.0 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Modulith event publications](https://docs.spring.io/spring-modulith/reference/events.html)
- [Boot 4.0.9 dependency BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.0.9/spring-boot-dependencies-4.0.9.pom)
