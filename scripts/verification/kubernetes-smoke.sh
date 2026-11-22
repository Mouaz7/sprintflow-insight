#!/usr/bin/env bash
set -euo pipefail

traefik_chart=${1:?Provide the verified Traefik chart archive.}
context=kind-sprintflow-ci
namespace=sprintflow-ci
base_url=http://127.0.0.1:18080
umask 077
scratch=$(mktemp -d)
forward_pid=
cleanup() {
  if [[ -n "$forward_pid" ]]; then kill "$forward_pid" 2>/dev/null || true; fi
  rm -rf -- "$scratch"
}
trap cleanup EXIT

kube() { kubectl --context "$context" --namespace "$namespace" "$@"; }
h() { helm --kube-context "$context" --namespace "$namespace" "$@"; }

kind create cluster --name sprintflow-ci --config deploy/kind/cluster.yaml --wait 180s \
  --image kindest/node:v1.36.4@sha256:099e049362a1526b2db71494e1947aae99bd16290d7c895f2b7ea312e3cbfaed
kind load docker-image --name sprintflow-ci sprintflow-k8s:previous sprintflow-k8s:current
kube create namespace "$namespace"
helm --kube-context "$context" --namespace traefik install traefik "$traefik_chart" \
  --create-namespace --wait --timeout 4m \
  --set service.spec.type=NodePort --set ports.web.nodePort=30080

kube create secret generic sprintflow-runtime \
  --from-literal=SPRINTFLOW_DB_URL=jdbc:postgresql://postgres:5432/sprintflow \
  --from-literal=SPRINTFLOW_DB_USER=sprintflow \
  --from-literal=SPRINTFLOW_DB_PASSWORD="$(openssl rand -hex 24)" \
  --from-literal=SPRINTFLOW_ENCRYPTION_KEY="$(openssl rand -base64 32)" \
  --from-literal=SPRINTFLOW_BROKER_LOGIN=sprintflow \
  --from-literal=SPRINTFLOW_BROKER_PASSCODE="$(openssl rand -hex 24)"
kube apply -f deploy/kind/dependencies.yaml
kube rollout status deployment/postgres --timeout=180s
kube rollout status deployment/rabbitmq --timeout=180s

install() {
  h upgrade --install "$1" deploy/helm/sprintflow -f deploy/kind/values.yaml \
    --set image.repository=sprintflow-k8s --set "image.tag=$2" \
    --wait --timeout 5m "${@:3}"
}

curl_args=(--silent --show-error --fail-with-body --max-time 15 --cookie "$scratch/session" --cookie-jar "$scratch/session")
touch "$scratch/session"
post() {
  local path=$1 body=$2 csrf header
  csrf=$(curl "${curl_args[@]}" "$base_url/api/auth/csrf")
  header=$(jq -er '.headerName + ": " + .token' <<< "$csrf")
  curl "${curl_args[@]}" -H "$header" -H 'Content-Type: application/json' \
    --data "$body" "$base_url/api$path"
}
verify_project() {
  curl "${curl_args[@]}" "$1/api/projects/$project_id" |
    jq -e --arg name "$project_name" '.name == $name' >/dev/null
}

install sprintflow previous
curl -fsS --retry 15 --retry-all-errors --retry-delay 2 --max-time 5 "$base_url/actuator/health"
project_name='Kubernetes N-1 compatibility fixture'
post /auth/register "$(jq -n --arg password "Qa-$(openssl rand -hex 16)" \
  '{email:"k8s-upgrade@example.test", displayName:"Kubernetes QA", password:$password}')" >/dev/null
project_id=$(post /projects "$(jq -n --arg name "$project_name" '{name:$name}')" | jq -er '.id')
verify_project "$base_url"

install sprintflow current
kube rollout status deployment/sprintflow --timeout=120s
verify_project "$base_url"
kube exec deployment/postgres -- psql -U sprintflow -d sprintflow -Atc \
  "select count(*) from flyway_schema_history where version='38' and success" | grep -qx 1

# Start the previous image against the expanded schema and reuse the original JDBC session.
install sprintflow-previous previous --set ingress.enabled=false --set autoscaling.enabled=false
kube port-forward svc/sprintflow-previous 18081:8081 >"$scratch/port-forward.log" 2>&1 &
forward_pid=$!
curl -fsS --retry 10 --retry-all-errors --retry-delay 1 --max-time 5 http://127.0.0.1:18081/actuator/health
verify_project http://127.0.0.1:18081
h uninstall sprintflow-previous --wait --timeout 120s
kube get hpa,pdb,networkpolicy
echo 'Verified the previous image and its original session before and after migration V38.'
