# C6 Kubernetes verification

The quality workflow builds the previous image from the PR base and consumes the current image from its Docker verification job. Both versions run in a disposable kind cluster behind Traefik. PostgreSQL and RabbitMQ run as separate services inside the fixture.

| Check | Evidence |
| --- | --- |
| Schema expansion | `EventPublicationClaimsIntegrationTest` writes with the old SQL shape before and after V38, exercises a competing row lock and verifies repeatable claims only after lease expiry. |
| Shared presence | `ClusterPresenceIntegrationTest` uses two independent registries and connections, combines multiple tabs for one member and expires leases from a departed replica. |
| Relay configuration | `RelaySettingsProperties` uses jqwik seed `2026112106` with 100 valid and 100 invalid samples. Boundary and redaction checks also run in JUnit. PIT includes `RelaySettings`. |
| Previous image compatibility | The shell check saves the previous version's session cookie and project, reads through Ingress following the rolling upgrade, then repeats the read on a newly started previous image against the same expanded database. |
| WebSocket recovery | Chromium records the serving pod from the WebSocket upgrade response, removes that pod, observes the socket close, writes three tasks while reconnection is held, and checks the browser receives their exact consecutive sequences and displays each task once. A fourth task arrives via the restored broker connection. |

Reproduce the Kubernetes portion from the repository root on a Linux AMD64 Docker host. Set `BASE_SHA` to the base commit to test.

```sh
tools="$(mktemp -d)"
bash scripts/verification/kubernetes-tools.sh "$tools"
export PATH="$tools:$PATH"
previous="$(mktemp -d)"
git archive "$BASE_SHA" | tar -x -C "$previous"
docker build -t sprintflow-k8s:previous "$previous"
docker build -t sprintflow-k8s:current .
bash scripts/verification/kubernetes-smoke.sh "$tools/traefik.tgz"
npm ci --prefix frontend/e2e
(
  cd frontend/e2e
  npx playwright install --with-deps chromium
  E2E_KIND_CONTEXT=kind-sprintflow-ci E2E_BASE_URL=http://127.0.0.1:18080 \
    npx playwright test kubernetes-failover.e2e.ts --retries=0
)
kind delete cluster --name sprintflow-ci
```

CI attaches the ingress test report and its replay evidence. The kind fixture tests rollout, session continuity and failover. Its default network does not demonstrate NetworkPolicy enforcement, and it has no Metrics API for observing automatic scaling. Production storage, TLS and broker redundancy depend on the deployed services.
