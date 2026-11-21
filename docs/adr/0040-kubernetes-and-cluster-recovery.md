# ADR 0040 — Kubernetes and cluster recovery

Status accepted.

## Deployment

The Helm chart in `deploy/helm/sprintflow` configures a rolling Deployment with at least two replicas, preferred node separation, a CPU HPA, a disruption budget, startup and readiness HTTP probes and a TCP liveness probe. Startup and readiness use the aggregate database and relay status. Terminating pods serve for five seconds while Ingress removes their endpoints. CPU autoscaling requires the Metrics API. A CNI with NetworkPolicy support restricts incoming requests to the Ingress namespace and outbound traffic to cluster DNS, PostgreSQL and RabbitMQ. Add explicit `networkPolicy.extraEgress` rules for enabled integrations such as telemetry or SMTP. Keep the application's management listener outside public Ingress.

Provision PostgreSQL and RabbitMQ with the STOMP plugin and create a Kubernetes Secret in the application namespace. The Secret must contain `SPRINTFLOW_DB_URL`, `SPRINTFLOW_DB_USER`, `SPRINTFLOW_DB_PASSWORD`, `SPRINTFLOW_ENCRYPTION_KEY`, `SPRINTFLOW_BROKER_LOGIN` and `SPRINTFLOW_BROKER_PASSCODE`. Configure a dedicated RabbitMQ user and virtual host accessible only over the internal network. Set `publicUrl` to the browser origin and keep secure cookies enabled with TLS. Use a pre-provisioned ReadWriteMany claim at `uploads.existingClaim` for shared uploads. The scratch defaults are suitable for temporary fixtures.

```sh
helm upgrade --install sprintflow deploy/helm/sprintflow \
  --namespace sprintflow --create-namespace \
  --values sprintflow-production-values.yaml \
  --set-string "image.digest=$IMAGE_DIGEST" --wait
```

Set `IMAGE_DIGEST` to the published SHA-256 digest. The values file supplies the existing Secret, public URL, ingress settings, storage claim, and broker address. For a managed database outside the namespace, configure appropriate egress CIDRs and ports for the database in `networkPolicy.extraEgress`.

## Shared state and recovery

Spring Session JDBC sessions and ShedLock entries are shared in PostgreSQL. The existing session cookie, WebSocket origin and STOMP authorization checks apply on every replica. RabbitMQ relays existing topic frames. Project events remain in the sequenced PostgreSQL event log. Browser reconnects fetch the replay after their last applied sequence. A gap beyond the retention window triggers the existing full refresh. Viewer leases contain a user ID, display name and optional task reference. Each replica renews only its authorized WebSocket sessions. A replica that exits abruptly stops renewing and its viewers expire.

V38 is an expand migration. It adds a nullable lease column and index for the event publication registry and creates a separate presence table. Older application binaries can use the expanded schema unchanged. A claim reserves at most 100 outstanding publications for two minutes using PostgreSQL row locks and skips rows held by another worker. The scheduler resubmits the claimed entries and retries expired leases. Listeners can execute again after a crash or slow lease expiry, so their writes remain idempotent. Older replicas still use their earlier retry policy during the rolling upgrade. Deploy a future contract migration only after all writers and rollback versions that use the superseded schema have been retired. A rollback keeps V38 applied and runs the previous image against it.

| Threat | Control |
| --- | --- |
| Spoofing | Shared authenticated session, allowed origin and STOMP CSRF on each connection. Dedicated broker credentials are held in a Kubernetes Secret. |
| Tampering | Authorized application destinations, database-enforced sequences and atomic outbox claims. |
| Repudiation | Existing application audit and request tracing work across replicas. |
| Information disclosure | Internal broker and database traffic, no public metrics, minimal viewer data with expiry. The pod-identifying handshake header is enabled only by the kind fixture. |
| Denial of service | Local frame and subscription limits, rolling readiness gate, HPA and replay window. |
| Elevation of privilege | Existing project role checks also apply to replay and subscriptions. Application pods have no Kubernetes API token and run unprivileged. |

[Replay and upgrade verification](../evaluation/kubernetes.md).
