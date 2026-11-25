# Observability & Monitoring

Runtime visibility for HMS — metrics, health, alerting, and the golden signals an on-call engineer
needs to detect and diagnose production issues on a 24×7 hospital system. This is the
application-instrumentation + config layer; standing up Prometheus/Grafana/Alertmanager is an
operator step (this doc tells you exactly how).

> Complements the existing [health verification](../deployment/DEPLOYMENT.md#health-verification)
> and [deployment manifests](../deployment/DEPLOYMENT.md#deployment-manifest-audit-record).

---

## What the app exposes

| Endpoint               | Auth                 | Purpose                                             |
| ---------------------- | -------------------- | --------------------------------------------------- |
| `/api/public/health`   | public               | liveness probe (deploy gate, uptime checks)         |
| `/actuator/health`     | public               | readiness (aggregates DB, Redis, disk)              |
| `/actuator/info`       | public               | build + git provenance (version, commit, branch)    |
| `/actuator/prometheus` | **Super Admin only** | Prometheus metrics scrape (via the management port) |
| `/actuator/metrics`    | **Super Admin only** | ad-hoc metric browsing                              |

Micrometer auto-instruments, with **no business-code changes**:

- **HTTP** — `http_server_requests_seconds_*` (rate, errors by status, latency histograms → p95/p99)
- **JVM** — heap/non-heap, GC, threads, classes
- **Database** — `hikaricp_connections_*` (active/idle/max/pending)
- **Web server** — Tomcat sessions/threads
- **Flyway** — migration state
  Every meter is tagged `application` + `environment` (via `MetricsConfig`) so one Prometheus/Grafana
  serves staging and production.

Latency histograms + SLO buckets are enabled (`application.properties`) so p95/p99 and
error-budget queries work out of the box. Add custom business metrics later with Micrometer
`Counter`/`Timer` or `@Timed` (the `TimedAspect` is wired) — e.g. patients registered, bills raised.

---

## Securing the scrape (important — metrics are not public)

Every Actuator endpoint except health and info is **Super Admin only** (`SecurityConfig`): metrics
describe the whole platform, so a hospital login must not read them (until this rule, any logged-in
user could). Prometheus has no login, so scrape through a port the public vhost never exposes:

- **Scrape token (recommended):** set `METRICS_SCRAPE_TOKEN` (32+ random characters, e.g.
  `openssl rand -hex 32`) on the backend service and give Prometheus the same value as a bearer
  token (`authorization.credentials_file` in
  [`scrape-config-example.yml`](../../monitoring/prometheus/scrape-config-example.yml)). The token
  unlocks `/actuator/prometheus` only (`MetricsScrapeAuthFilter`, role `METRICS_SCRAPER`), never
  `/actuator/metrics` or any tenant endpoint. Unset means disabled.
- **Until it is set, no alert can fire:** Prometheus cannot log in, so every scrape is refused and
  `HMSInstanceDown` is the only rule that would trigger.

Never add these endpoints to `permitAll` / expose them through the public vhost.

---

## Stand up the stack (operator steps)

1. **Prometheus** — merge [`monitoring/prometheus/scrape-config-example.yml`](../../monitoring/prometheus/scrape-config-example.yml)
   into `prometheus.yml`; load [`monitoring/prometheus/hms-alerts.yml`](../../monitoring/prometheus/hms-alerts.yml)
   via `rule_files`.
2. **Grafana** — import [`monitoring/grafana/hms-dashboard.json`](../../monitoring/grafana/hms-dashboard.json)
   (request rate, 5xx %, p95/p99 latency, JVM heap %, DB pool). Select your Prometheus datasource.
3. **Alertmanager** — route the alerts below to Slack/email/on-call (reuse the deploy Slack webhook
   if you like).
4. **Host metrics (optional but recommended)** — add `node_exporter` for CPU/mem/disk host alerts
   (the deploy-time `verify-deployment.sh` already checks these per deploy).

---

## Alerts shipped

| Alert                     | Severity | Fires when               |
| ------------------------- | -------- | ------------------------ |
| `HMSInstanceDown`         | critical | scrape target down >1m   |
| `HMSHighErrorRate`        | critical | 5xx rate >5% for 5m      |
| `HMSDBDown`               | critical | no DB connections for 2m |
| `HMSHighLatencyP95`       | warning  | p95 >1s for 10m          |
| `HMSHighHeapUsage`        | warning  | heap >90% for 10m        |
| `HMSDBPoolNearExhaustion` | warning  | pool >90% for 5m         |

Tune thresholds against real baselines after a week of data.

---

## The four golden signals (where to look)

- **Latency** — dashboard p95/p99; `HMSHighLatencyP95`.
- **Traffic** — dashboard request rate.
- **Errors** — dashboard 5xx %; `HMSHighErrorRate`.
- **Saturation** — JVM heap %, DB pool, host CPU/mem/disk (node_exporter).

## What this does NOT include (future / infra scope)

Distributed tracing (OpenTelemetry), centralized log aggregation (Loki/ELK), synthetic monitoring,
and long-term metric storage/HA for Prometheus itself. These need infrastructure beyond the current
single VPS and are tracked in the [Production Readiness Review](../PRODUCTION_READINESS_REVIEW.md).

## Manual configuration required

- Enable a secure scrape model (A or B above).
- Deploy Prometheus + Grafana + Alertmanager (and optionally node_exporter) on/near the VPS.
- Wire Alertmanager to your notification channel and set an on-call rotation.
