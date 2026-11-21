# ADR 0039 — Observability and service-level objectives

Status accepted.

## Metrics and traces

The public app retains `/actuator/health` as a readiness check with only the aggregate state. Spring Boot Actuator runs on a separate management listener. Compose uses internal port 8082. A direct Java process binds management to loopback and chooses an available port unless configured otherwise. Prometheus is available only on the management listener. No management, Prometheus or Tempo port is published by Compose.

Spring and Micrometer provide request counts and duration histograms by normalized route, method and outcome. The 300 ms bucket records requests meeting the latency objective. Application counters track committed task creations and events sent to the realtime broker. Outbox gauges report unfinished publications and the oldest queued age. They refresh separately from scrapes and retain the last value during a database outage. Aggregate these shared-database gauges with `max` across instances.

Console logs use newline-delimited JSON by default. Unexpected HTTP errors carry an `errorId` in the response and in the log entry. When a trace exists, the same `traceId` appears in the RFC 9457 response and log. The error log stores the exception type and first frame. Request error paths drop queries and redact email addresses. Server span attributes use normalized routes without full URLs. OTLP export is opt-in with a default sampling probability of 10%.

## Run the profile

Use the existing `.env` bootstrap and run the application with the optional observer services.

```sh
SPRINTFLOW_TRACING_EXPORT=true docker compose --profile observability up -d
```

Grafana is available at `http://127.0.0.1:3000`. Open the **SprintFlow** folder and **SprintFlow RED and SLOs** dashboard. Prometheus and Tempo are provisioned data sources. Look up a response `traceId` using Tempo in **Explore**. Set `SPRINTFLOW_TRACE_SAMPLE_RATE=1` for a short local trace verification. Prometheus retains 30 days and Tempo uses temporary local storage in this profile.

## Objectives and alerts

| Objective | Rolling 30-day success target | Budget |
| --- | ---: | ---: |
| API availability | 99.5% of routed API requests return a response outside HTTP 5xx | 0.5% |
| API latency | 95% of routed API requests complete in at most 300 ms | 5% |

The rules measure `/api` route templates. Internal health checks and traffic without a resolved route are outside these objectives. The dashboard shows rate, error ratio and p95 duration. Fast alerts require burn over 14.4 times the budget in both a 5-minute and 1-hour window. Slow alerts require burn over six times the budget in both a 30-minute and 6-hour window. Both conditions hold for two minutes before firing. Prometheus evaluates the rules inside the profile. CI checks the dashboard, live scrape and that Tempo can retrieve an exported trace.

| Threat | Control |
| --- | --- |
| Spoofing | Trace identifiers support correlation. Application authorization uses the signed-in identity. |
| Tampering | Pinned images, a dedicated Compose network and read-only Grafana provisioning. |
| Repudiation | A response reference joins the request trace and the corresponding structured log entry. |
| Information disclosure | Only aggregate public health. Metrics and trace listeners stay off published ports. Span attributes and error logs exclude request queries. |
| Denial of service | Bounded histogram labels, scheduled gauge refresh and sampled traces. |
| Elevation of privilege | Management listeners grant no workspace access. The host-published Grafana account is a loopback viewer. |

See [Spring Boot tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html) and [Tempo monolithic deployment](https://grafana.com/docs/tempo/latest/set-up-for-tracing/setup-tempo/deploy/locally/linux/).
