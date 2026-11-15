# ADR 0014: CI pipeline and software supply chain

Status: accepted for A8 (#9).

## Context

Before A8 the pipeline had four jobs (`quality`, `docker`, `security`, `mutation`), all required by branch protection on `main`. It had no dependency caches, used mutable action tags, built the image without a layer cache and installed curl in the runtime image only for the health check. Nothing published an image, so there was nothing to sign. The browser checks in `scripts/verification` ran by hand.

## Decision

**One build, promoted.** The `quality` job runs the only `mvn verify` of the pipeline. CodeQL analyses Java with `build-mode: none`, so it does not run a second Maven build, and the Docker build skips tests. The `docker` job builds the image once with a Buildx GitHub Actions cache, runs the container smoke test and uploads the image tar. Later jobs (`e2e`, `image-scan`, `zap-baseline`, `k6-smoke`, `lighthouse`, `publish`) load that exact image. On `main`, `publish` pushes the image that the other jobs tested; it does not rebuild it.

**Required checks keep their names.** The four required job ids are unchanged and do not have a `name:`. New jobs are additions, so branch protection still matches. No existing job needs a secret, so pull requests from forks still run them.

**Signing and provenance without long-lived keys.** `publish` runs only on pushes to `main`. It is the only job with `packages: write`, `id-token: write` and `attestations: write`. It signs the GHCR digest with cosign keyless (Fulcio certificate from the GitHub OIDC token, recorded in Rekor). It also adds GitHub artifact attestations: SLSA build provenance (`actions/attest-build-provenance`) and the image's CycloneDX SBOM (`actions/attest-sbom`). We chose keyless signing over a stored cosign key because there is no secret to leak, rotate or hand to forks. Verification is tied to the workflow identity instead.

**SBOMs at two levels.** The Boot-managed `cyclonedx-maven-plugin` embeds `META-INF/sbom/application.cdx.json` in every jar. `cyclonedx-npm` describes the shipped frontend dependencies (`--omit dev`, because only React ends up in the bundle). Trivy describes the whole image, including OS packages, and that image SBOM is the one attested in GHCR.

**Pinned inputs.** Every third-party action is pinned to a full commit SHA with a version comment. Base images and the Compose PostgreSQL image are pinned by digest. Scanner binaries (gitleaks, osv-scanner, Trivy) are downloaded by version and checked against a SHA-256. Trivy runs as a checksum-verified binary rather than through `aquasecurity/trivy-action`, which keeps the scanner itself out of the action supply chain. `repository-check.ts` fails CI when an action is not SHA-pinned or a `FROM` has no digest. Dependabot proposes weekly grouped updates for Maven, npm (three packages), GitHub Actions, Docker and Compose. A seven-day cooldown gives a compromised release time to be noticed and yanked before it is proposed.

**Least privilege.** The workflow-level token has no permissions (`permissions: {}`). Each job asks only for what it uses. Checkouts do not persist credentials.

**Scanners and their gates.**

| Tool | Scope | Gate |
|---|---|---|
| CodeQL `security-extended` | Actions workflows, Java, TypeScript | Code-scanning alerts |
| gitleaks, osv-scanner | History, all lockfiles and `pom.xml` | Required `security` job fails |
| Trivy | Image OS and Java packages, secrets, misconfiguration | Fails on fixable CRITICAL; HIGH and above go to code scanning |
| OWASP ZAP baseline | Passive scan of the running app (traditional and AJAX spider) | Fails on FAIL/WARN unless `.zap/rules.tsv` overrides a rule with a reason |
| OpenSSF Scorecard | Repository practices | Badge and code-scanning results |

**Healthcheck without curl.** A 20-line JDK class (`docker/HealthCheck.java`) is compiled in the build stage and probes `/actuator/health`. The runtime image keeps its `HEALTHCHECK`, so Compose `--wait` still works. Orchestrators should use their own HTTP probes on the same endpoint.

**Performance budgets are lab checks.** Lighthouse CI fails the non-required `lighthouse` job when the median desktop run exceeds LCP 2.5 s, TBT 200 ms, CLS 0.1 or 175 kB of transferred script. INP needs real interactions, so Total Blocking Time is its lab proxy. The mobile profile (simulated slow 4G, 4x CPU slowdown) is reported as warnings: the sign-in page's median mobile LCP is about 2.9 s, because the page renders only after the SPA bundle and Google Fonts load. The k6 smoke profile checks error rate and p95 < 500 ms at two virtual users. It catches regressions; it does not measure capacity.

## Consequences

- A pull request runs about ten jobs. The four required checks are unchanged, and the new jobs are informative until the owner adds them to branch protection.
- The image tar artifact costs upload time but guarantees that what is scanned, tested and published is one digest.
- `publish` is only exercised on `main`. A failure there does not block merging, but it does leave `main` without a signed image until it is fixed.
- Verify a published image with:

  ```sh
  cosign verify ghcr.io/mouaz7/sprintflow-insight:main \
    --certificate-identity-regexp '^https://github.com/Mouaz7/sprintflow-insight/\.github/workflows/quality\.yml@refs/heads/main$' \
    --certificate-oidc-issuer https://token.actions.githubusercontent.com
  gh attestation verify oci://ghcr.io/mouaz7/sprintflow-insight:main --repo Mouaz7/sprintflow-insight
  ```

- Visual regression (screenshots in light, dark and mobile) is not part of A8. It comes with the design system and Storybook in A7.

## Sources

- [GitHub artifact attestations](https://docs.github.com/en/actions/security-for-github-actions/using-artifact-attestations)
- [Sigstore cosign keyless signing](https://docs.sigstore.dev/cosign/signing/overview/)
- [SLSA provenance](https://slsa.dev/spec/v1.0/provenance)
- [Security hardening for GitHub Actions](https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions)
- [CycloneDX](https://cyclonedx.org/specification/overview/)
- [Lighthouse Total Blocking Time](https://developer.chrome.com/docs/lighthouse/performance/lighthouse-total-blocking-time)
