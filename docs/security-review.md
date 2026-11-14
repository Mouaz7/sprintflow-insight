# Dependency and secret review

The dependency check covers both npm lockfiles and the resolved Maven dependency graph using OSV.
The release gate rejects untreated high/critical findings; lower-severity findings also require review.
Keep the raw report as a CI artifact, and reassess when code or dependency versions change.

The candidate upgrades Spring Boot and applies compatible patch overrides for Jackson, Tomcat, Log4j
and the PostgreSQL driver. Exact versions are pinned in the Maven properties and npm lockfiles.

## Reviewed applicability exceptions

Two Spring Framework 6.2 advisories require features absent from this application:

| Advisory                                                     | Required feature                                  | Evidence and review limit                                                                                   |
| ------------------------------------------------------------ | ------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) | Streaming server-rendered view fragments over SSE | No ResponseBodyEmitter/FragmentsRendering handlers; enforced by SecurityConfigurationTest                   |
| [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) | XsltView with an implicit wildcard view mapping   | No XsltViewResolver; SPA forwarding names the static view explicitly; enforced by SecurityConfigurationTest |

OSV/GitHub severity differs from the vendor classification. These are applicability decisions, not claims
that the affected library version contains a fix. The exceptions were reassessed on 2026-11-14, expire on 2027-01-31 and must be
reassessed before adding SSE, server-rendered views or XSLT. The open-source fixes are in Spring 7.0.9;
the Spring 6 fix requires the vendor's enterprise distribution. The planned Spring Boot 4 upgrade removes
the need for these exceptions. Do not suppress future findings automatically.

## Secret checks

Gitleaks scans both committed history and the candidate sources. `.env`, uploads, backups, artifacts and
build output remain excluded from Git. No paths, values or test directories are
allowlisted. Browser checks generate disposable passwords at run time.
