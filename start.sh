#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
export SPRINTFLOW_ROOT="$ROOT_DIR"
# shellcheck source=scripts/env-bootstrap.sh
source "$ROOT_DIR/scripts/env-bootstrap.sh"
bootstrap_env

is_wsl() {
  grep -qiE 'microsoft|wsl' /proc/version 2>/dev/null
}

start_with_windows_docker() {
  command -v powershell.exe >/dev/null 2>&1 || return 1
  command -v wslpath >/dev/null 2>&1 || return 1

  local windows_launcher
  windows_launcher="$(wslpath -w "$ROOT_DIR/start.ps1")"
  printf 'Using Docker Desktop on Windows. It will start automatically if needed.\n'
  local result=0
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$windows_launcher" || result=$?
  exit "$result"
}

app_port="$(read_port SPRINTFLOW_APP_PORT 8081)"

if ! command -v docker >/dev/null 2>&1 || ! docker info --format '{{.ServerVersion}}' >/dev/null 2>&1; then
  if is_wsl; then
    start_with_windows_docker || die 'Docker is unavailable in WSL and the Windows fallback could not start. Install Docker Desktop on Windows, then run ./start.sh again.'
  fi
  command -v docker >/dev/null 2>&1 || die 'Install Docker Desktop (macOS) or Docker Engine with Compose (Linux), then run ./start.sh again.'
  if [[ "$(uname -s)" == Darwin ]]; then
    printf 'Starting Docker Desktop...\n'
    open -a Docker || die 'Install Docker Desktop, then run ./start.sh again.'
  elif [[ "$(id -u)" == 0 ]] && command -v systemctl >/dev/null 2>&1; then
    printf 'Starting Docker Engine...\n'
    systemctl start docker || die 'Could not start Docker Engine.'
  else
    die 'Start Docker Engine, then run ./start.sh again.'
  fi
  for ((attempt=1; attempt<=36; attempt++)); do
    if docker info >/dev/null 2>&1; then break; fi
    printf 'Waiting for Docker (%s/36)...\n' "$attempt"
    sleep 5
  done
  docker info >/dev/null 2>&1 || die 'Docker did not become ready within three minutes.'
fi
docker compose version >/dev/null 2>&1 || die 'Docker Compose v2 is required. Update Docker Desktop or install the Docker Compose plugin.'

printf 'Building and starting SprintFlow. The first build downloads dependencies and can take several minutes.\n'
cd "$ROOT_DIR"
docker compose --env-file "$ENV_FILE" up -d --build --wait --wait-timeout 180 || {
  docker compose --env-file "$ENV_FILE" logs --tail 80 app postgres >&2 || true
  die 'Docker startup failed. See the logs above.'
}
printf 'SprintFlow is ready at http://localhost:%s\n' "$app_port"
