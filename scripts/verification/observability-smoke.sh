#!/usr/bin/env bash
set -euo pipefail

project=${1:?Supply the isolated Compose project name}
grafana_url="http://127.0.0.1:${SPRINTFLOW_GRAFANA_PORT:-3000}"
app_url="http://127.0.0.1:${SPRINTFLOW_APP_PORT:-8081}"
compose=(docker compose -p "$project" --profile observability)

"${compose[@]}" up -d --no-build --wait --wait-timeout 180
"${compose[@]}" exec -T prometheus promtool check config /etc/prometheus/prometheus.yml
"${compose[@]}" exec -T prometheus promtool test rules /etc/prometheus/slo.test.yml

for attempt in {1..35}; do
  if curl -fsS --max-time 4 "$grafana_url/api/dashboards/uid/sprintflow-red" |
    node -e 'const item = JSON.parse(require("node:fs").readFileSync(0)); process.exit(item.dashboard?.uid === "sprintflow-red" ? 0 : 1)'; then
    break
  fi
  if [[ $attempt == 35 ]]; then exit 1; fi
  sleep 2
done

status=$(curl -sS --max-time 4 -o /dev/null -w '%{http_code}' "$app_url/actuator/prometheus")
[[ $status == 404 || $status == 403 ]]

prometheus="$grafana_url/api/datasources/proxy/uid/sprintflow-prometheus"
for attempt in {1..25}; do
  if curl -fsS --max-time 4 --get --data-urlencode 'query=up{job="sprintflow"}' "$prometheus/api/v1/query" |
    node -e 'const item = JSON.parse(require("node:fs").readFileSync(0)); process.exit(item.data?.result?.some((row) => row.value[1] === "1") ? 0 : 1)'; then
    break
  fi
  if [[ $attempt == 25 ]]; then exit 1; fi
  sleep 2
done

trace_id=$(openssl rand -hex 16)
parent_id=$(openssl rand -hex 8)
curl -fsS --max-time 5 -o /dev/null -H "traceparent: 00-$trace_id-$parent_id-01" "$app_url/api/auth/csrf"
tempo="$grafana_url/api/datasources/proxy/uid/sprintflow-tempo"
for attempt in {1..35}; do
  if curl -fsS --max-time 4 -o /dev/null "$tempo/api/traces/$trace_id"; then
    echo 'Prometheus scrape, Tempo trace and provisioned Grafana dashboard verified.'
    exit 0
  fi
  sleep 2
done
exit 1
