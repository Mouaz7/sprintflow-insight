#!/usr/bin/env bash
set -euo pipefail

results=${1:?Provide the evaluation output directory.}
k6_bin=${EVAL_K6_BINARY:-k6}
"$k6_bin" version > "$results/k6-version.txt"

count_statements() {
  PGDATABASE="$1" psql -X -qAt -v ON_ERROR_STOP=1 -c "
    select coalesce(sum(calls), 0)
    from pg_stat_statements
    where dbid = (select oid from pg_database where datname=current_database())
      and query not like '%pg_stat_statements%'
  "
}

sample() {
  local label=$1 base=$2 database=$3 first last version
  if [[ ! "$base" =~ ^http://127\.0\.0\.1:[0-9]{1,5}$ ]]; then
    echo "Use a disposable HTTP application bound to 127.0.0.1 for $label." >&2
    exit 1
  fi
  curl -fsS --max-time 10 "$base/actuator/health" >/dev/null
  version=$(PGDATABASE="$database" psql -X -qAt -v ON_ERROR_STOP=1 -c 'show server_version')
  first=$(count_statements "$database")
  BASE_URL="$base" K6_PROFILE=evaluation "$k6_bin" run \
    --out "json=$results/http-$label-raw.json" scripts/performance/workspace-read.k6.ts
  last=$(count_statements "$database")
  if (( last < first )); then
    echo "Statement statistics changed outside the measurement in $label." >&2
    exit 1
  fi
  K6_BUILD_LABEL=$(cat "$results/k6-version.txt") K6_DATABASE_LABEL="$version" \
    node scripts/verification/summarize-performance.ts \
      "$results/http-$label-raw.json" "$results/http-$label.json" "$label"
  printf '%s,%d\n' "$label" "$((last - first))" >> "$results/http-sql-counts.csv"
}

printf 'variant,sql_calls_full_workload\n' > "$results/http-sql-counts.csv"
sample before "${EVAL_HTTP_BEFORE_URL:?}" "${EVAL_HTTP_BEFORE_DATABASE:?}"
sample after "${EVAL_HTTP_AFTER_URL:?}" "${EVAL_HTTP_AFTER_DATABASE:?}"
