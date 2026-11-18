# Dependency and secret review

The dependency check covers all three npm lockfiles and the resolved Maven dependency graph using OSV.
The release gate rejects untreated high/critical findings; lower-severity findings also require review.
Keep the raw report as a CI artifact, and reassess when code or dependency versions change.

The candidate upgrades Spring Boot and applies compatible patch overrides for Jackson, Tomcat, Log4j
and the PostgreSQL driver. Exact versions are pinned in the Maven properties and npm lockfiles.

## Reviewed applicability exceptions

The former Spring 6 exceptions were removed by the Boot 4 upgrade. One development-tooling applicability decision is currently active:

| Advisory | Assessment | Review limit |
| --- | --- | --- |
| [GHSA-fc5q-39xc-vjpf / CVE-2026-112366](https://github.com/advisories/GHSA-fc5q-39xc-vjpf) | `picomatch` 4.0.7 can backtrack excessively when given hostile repeated-extglob **patterns**. It is used only by Vite/Vitest, Storybook/Rollup, Babel and ESLint tooling with repository-owned configuration patterns. `npm --prefix frontend ls --omit=dev picomatch` is empty; application source has no picomatch/glob API, and the Java runtime ships no Node tooling. Application search/filters do not use it. | Reviewed 2026-11-18; OSV exception expires 2026-12-02. Reassess earlier if tool patterns can come from users or dependencies change. |

The [upstream releases](https://github.com/micromatch/picomatch/releases) and advisory list no patched version as of the review; npm's latest is 4.0.7. This decision does **not** claim the library is fixed. It accepts the bounded development-tooling exposure while an upstream fix is unavailable. Contributor-controlled build configuration already executes within the isolated CI job; do not feed application input to these glob parsers or expose development servers as production services.

`scripts/verification/repository-check.ts` rejects a picomatch entry without `dev: true` in any of the three lockfiles. This guard runs before builds and prevents the exception silently spreading into production dependencies. The exception names only this advisory, expires automatically in OSV and leaves all other dependency/secret gates enabled. Replace it with a pinned corrected package when available; do not suppress future findings automatically.

## Secret checks

Gitleaks scans both committed history and the candidate sources. `.env`, uploads, backups, artifacts and
build output remain excluded from Git. No paths, values or test directories are
allowlisted. Browser checks generate disposable passwords at run time.

## Application controls

The control-by-control mapping is in [ASVS coverage](security/asvs.md). Design decisions are recorded as [ADRs](adr/README.md).
