#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$ROOT_DIR/.env"
die() { printf 'Error: %s\n' "$*" >&2; exit 1; }
umask 077

# Portable on GNU/Linux and macOS; never evaluate .env as shell code.
write_setting() {
  local key="$1" value="$2" temporary
  temporary="$(mktemp "$ROOT_DIR/.env.XXXXXX")"
  if awk -v key="$key" -v value="$value" '
    { sub(/\r$/, "") }
    index($0, key "=") == 1 { print key "=" value; found=1; next }
    { print }
    END { if (!found) print key "=" value }
  ' "$ENV_FILE" > "$temporary"; then
    mv -- "$temporary" "$ENV_FILE"
  else
    rm -f -- "$temporary"
    die 'Could not update .env.'
  fi
}

read_setting() {
  local key="$1" fallback="$2" value
  value="$(sed -n "s/^${key}=//p" "$ENV_FILE" | tr -d '\r' | tail -n 1)"
  printf '%s' "${value:-$fallback}"
}

if [[ ! -f "$ENV_FILE" ]]; then
  [[ -f "$ROOT_DIR/.env.example" ]] || die '.env.example is missing.'
  # Local starts serve plain HTTP on localhost, so the template's secure-cookie default must be relaxed.
  sed 's/^SPRINTFLOW_COOKIE_SECURE=.*/SPRINTFLOW_COOKIE_SECURE=false/' "$ROOT_DIR/.env.example" >"$ENV_FILE"
  printf 'Created private .env settings. Keep this file for future starts.\n'
fi

# A pre-existing .env copied from .env.example may still have the blank template value.
# Fill only that blank value; preserve any configured password and all other settings.
if [[ -z "$(read_setting SPRINTFLOW_DB_PASSWORD '')" ]]; then
  password="$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')"
  write_setting SPRINTFLOW_DB_PASSWORD "$password"
  printf 'Generated a private database password in .env. Keep this file for future starts.\n'
fi

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

read_port() {
  local key="$1" fallback="$2" value
  value="$(read_setting "$key" "$fallback")"
  [[ "$value" =~ ^[0-9]{1,5}$ ]] && (( 10#$value >= 1 && 10#$value <= 65535 )) || die "$key must be between 1 and 65535."
  printf '%s' "$value"
}
app_port="$(read_port SPRINTFLOW_APP_PORT 8081)"
read_port SPRINTFLOW_DB_PORT 5432 >/dev/null

# Prepare permissions before the WSL fallback as well as direct Docker startup.
mkdir -p "$ROOT_DIR/backend/data"
[[ "$(read_setting SPRINTFLOW_CONTAINER_UID 1000)" != 1000 ]] || write_setting SPRINTFLOW_CONTAINER_UID "$(id -u)"
[[ "$(read_setting SPRINTFLOW_CONTAINER_GID 1000)" != 1000 ]] || write_setting SPRINTFLOW_CONTAINER_GID "$(id -g)"

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
