# Supply chain and CI security

This page describes how SprintFlow's build inputs and outputs are protected. The reasoning is in [ADR 0014](../adr/0014-ci-supply-chain.md), and the job list is in the [testing guide](../testing.md#ci-pipeline-a8).

## Inputs

- **Actions** are pinned to full commit SHAs with a version comment. `scripts/verification/repository-check.ts` fails CI for an unpinned `uses:` in a workflow or local composite action.
- **Images**: the Dockerfile's base images and the Compose PostgreSQL image are pinned by digest, and the repository check fails for a `FROM` without one. The ZAP scanner image is pinned by digest as well.
- **Scanner binaries** (gitleaks, osv-scanner, Trivy) are downloaded by exact version and verified against a SHA-256 before use.
- **Packages**: npm and Maven resolve from lockfiles and the Boot BOM. Exact versions are used for test tooling. A transitive package with a known advisory is overridden (for example `basic-ftp`, pulled in by Lighthouse CI's proxy support) instead of being waived.
- **Dependabot** (`.github/dependabot.yml`) proposes weekly grouped updates for Maven, npm (`frontend`, `frontend/e2e`, `scripts`), GitHub Actions, Dockerfile and Compose images. Each update waits for a seven-day cooldown first.

## Pipeline

- The token has no permissions at workflow level. Jobs request only what they need, and checkouts do not persist credentials. Only `publish` (pushes to `main`) can write packages, mint an OIDC token or write attestations. The only other write is code scanning: `image-scan`, CodeQL and Scorecard upload SARIF.
- Pull requests from forks run every required job without secrets. Their Trivy SARIF upload is skipped because the fork token is read-only, and the report stays in the job artifact.
- The image is built once. The image that is smoke-tested, scanned, end-to-end tested and ZAP-scanned is the one `publish` pushes.

## Outputs

| Output | Where | How to check |
|---|---|---|
| Backend SBOM (CycloneDX 1.6) | Inside the jar at `META-INF/sbom/application.cdx.json`; CI artifact `sbom` | `unzip -p app.jar META-INF/sbom/application.cdx.json` |
| Frontend SBOM (shipped dependencies) | CI artifact `sbom` (`frontend.cdx.json`) | `npm run sbom --prefix scripts` |
| Image SBOM (OS and Java packages) | CI artifact `image-scan`; attested in GHCR | `gh attestation verify oci://ghcr.io/mouaz7/sprintflow-insight:main --repo Mouaz7/sprintflow-insight --predicate-type https://cyclonedx.org/bom` |
| Signature | Sigstore (Fulcio certificate, Rekor entry), stored in GHCR | `cosign verify` with the identity in ADR 0014 |
| SLSA build provenance | GitHub attestation, stored in GHCR | `gh attestation verify oci://ghcr.io/mouaz7/sprintflow-insight:main --repo Mouaz7/sprintflow-insight` |

## Runtime image

The runtime stage is the Temurin 25 JRE on Ubuntu Jammy, running as UID 1000 with no package manager additions. The image has no curl or wget, and the `docker` job fails if one appears. The health check is a compiled JDK class (`docker/HealthCheck.java`) that calls `/actuator/health`. Orchestrators should use their own HTTP probes on that endpoint. Trivy fails the build on fixable CRITICAL vulnerabilities and reports HIGH and CRITICAL findings to code scanning.

## OWASP ZAP baseline decisions

`zap-baseline` passively scans the running app with the traditional and AJAX spiders, without signing in. Any alert fails the job unless `.zap/rules.tsv` overrides it. The overrides are:

| Rule | Decision | Reason |
|---|---|---|
| 10049 Non-Storable Content | Ignore | API responses are `no-store` on purpose |
| 10094 Base64 Disclosure | Ignore | The CSP nonce and CSRF token are random base64 values by design |
| 10109 Modern Web Application | Ignore | Informational: the app is a SPA, and the AJAX spider is already enabled |
| 90005 Sec-Fetch-Dest Header is Missing | Ignore | Concerns headers in ZAP's own requests, not server behaviour |
| 10055 CSP: Wildcard Directive | Info (pending) | `img-src` allows any `https:` image. Narrowing it needs a decision on which image sources the product supports |
| 90004 Cross-Origin-Embedder-Policy missing | Info (pending) | `require-corp` would block Google sign-in, Google Fonts and remote images unless they send CORP headers |

The two pending items are product decisions, not code defects. Change a rule back to `WARN` once it is resolved.
