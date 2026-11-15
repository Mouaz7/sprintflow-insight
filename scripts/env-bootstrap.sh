#!/usr/bin/env bash
# Shared .env bootstrap for start.sh and CI.
#
# Source it to get read_setting/write_setting/read_port and call bootstrap_env,
# or run it directly to create or complete .env without starting anything:
#   scripts/env-bootstrap.sh            # .env in the repository root
#   SPRINTFLOW_ENV_FILE=/tmp/x.env scripts/env-bootstrap.sh
#
# The file is never evaluated as shell code; values are read with sed.

SPRINTFLOW_ROOT="${SPRINTFLOW_ROOT:-$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)}"
ENV_FILE="${SPRINTFLOW_ENV_FILE:-$SPRINTFLOW_ROOT/.env}"

die() { printf 'Error: %s\n' "$*" >&2; exit 1; }

# Portable on GNU/Linux and macOS.
write_setting() {
  local key="$1" value="$2" temporary
  temporary="$(mktemp "$ENV_FILE.XXXXXX")"
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

read_port() {
  local key="$1" fallback="$2" value
  value="$(read_setting "$key" "$fallback")"
  [[ "$value" =~ ^[0-9]{1,5}$ ]] && (( 10#$value >= 1 && 10#$value <= 65535 )) || die "$key must be between 1 and 65535."
  printf '%s' "$value"
}

bootstrap_env() {
  umask 077
  if [[ ! -f "$ENV_FILE" ]]; then
    [[ -f "$SPRINTFLOW_ROOT/.env.example" ]] || die '.env.example is missing.'
    # Local starts serve plain HTTP on localhost, so the template's secure-cookie default must be relaxed.
    sed 's/^SPRINTFLOW_COOKIE_SECURE=.*/SPRINTFLOW_COOKIE_SECURE=false/' "$SPRINTFLOW_ROOT/.env.example" >"$ENV_FILE"
    printf 'Created private .env settings. Keep this file for future starts.\n'
  fi

  # A pre-existing .env copied from .env.example may still have the blank template value.
  # Fill only that blank value; preserve any configured password and all other settings.
  if [[ -z "$(read_setting SPRINTFLOW_DB_PASSWORD '')" ]]; then
    write_setting SPRINTFLOW_DB_PASSWORD "$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')"
    printf 'Generated a private database password in .env. Keep this file for future starts.\n'
  fi

  # The data-encryption key is generated once and never replaced automatically:
  # a new key would make existing encrypted values unreadable (rotation: docs/adr/0015).
  if [[ -z "$(read_setting SPRINTFLOW_ENCRYPTION_KEY '')" ]]; then
    write_setting SPRINTFLOW_ENCRYPTION_KEY "$(head -c 32 /dev/urandom | base64 | tr -d '\n')"
    printf 'Generated a private encryption key in .env. Keep this file (and a backup of it) for future starts.\n'
  fi

  read_port SPRINTFLOW_APP_PORT 8081 >/dev/null
  read_port SPRINTFLOW_DB_PORT 5432 >/dev/null

  # The container runs as this user so bind-mounted uploads stay writable.
  mkdir -p "$SPRINTFLOW_ROOT/backend/data"
  [[ "$(read_setting SPRINTFLOW_CONTAINER_UID 1000)" != 1000 ]] || write_setting SPRINTFLOW_CONTAINER_UID "$(id -u)"
  [[ "$(read_setting SPRINTFLOW_CONTAINER_GID 1000)" != 1000 ]] || write_setting SPRINTFLOW_CONTAINER_GID "$(id -g)"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  set -Eeuo pipefail
  bootstrap_env
fi
