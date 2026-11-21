#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."
results=${EVAL_OUTPUT_DIR:-artifacts/evaluation}
mkdir -p "$results"
results=$(realpath "$results")
unset DEBUG

(
  cd backend
  ./mvnw -B -ntp test -Dtest=SprintPlanBenchmarkTest,JiraRiskEvaluationTest \
    -Dtest.excludedGroups=none -Djacoco.skip=true \
    -Dbenchmark.sizes=20,50,100,200 -Dbenchmark.instances=8
)
classes=backend/target/classes
generated=backend/target/benchmark
mkdir -p "$generated/evaluation"
javac -cp "$classes" -d "$generated/evaluation" scripts/evaluation/ForecastEvaluation.java
forecast() {
  java -cp "$classes:$generated/evaluation" com.sprintflowinsight.reporting.ForecastEvaluation "$1"
}
forecast docs/evaluation/apache-jira-weekly.csv > "$results/forecast-jira.tsv"
node scripts/evaluation/synthetic-throughput.ts "$results/synthetic-throughput.csv"
forecast "$results/synthetic-throughput.csv" > "$results/forecast-synthetic.tsv"
cp "$generated"/sprint-plan.csv "$generated"/apache-jira-risk-{metrics,calibration}.csv "$results/"

if [[ -n ${EVAL_SUS_CSV:-} ]]; then
  node scripts/evaluation/sus.ts "$EVAL_SUS_CSV" "$results/sus.json"
else
  printf '%s\n' '{"sampleSize":0,"status":"responses-not-provided"}' > "$results/sus.json"
fi
if [[ -n ${EVAL_HTTP_BEFORE_URL:-} || -n ${EVAL_HTTP_AFTER_URL:-} ]]; then
  bash scripts/evaluation/http-comparison.sh "$results"
fi
if [[ -n ${EVAL_REALTIME_SINGLE_URL:-} ]]; then
  node scripts/evaluation/realtime-load.ts "$EVAL_REALTIME_SINGLE_URL" 1 "$results/realtime-single.json"
fi
if [[ -n ${EVAL_REALTIME_CLUSTER_URL:-} ]]; then
  node scripts/evaluation/realtime-load.ts "$EVAL_REALTIME_CLUSTER_URL" 2 "$results/realtime-cluster.json"
fi
printf 'Evaluation results written to %s\n' "$results"
