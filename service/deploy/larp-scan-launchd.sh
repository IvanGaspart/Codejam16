#!/usr/bin/env bash
# Wrapper for the LaunchAgent: load secrets then exec scan-loop.
set -euo pipefail

ENV_FILE="${HOME}/.config/larp-scan/env"
SCAN_LOOP="${HOME}/.local/bin/scan-loop"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "larp-scan-launchd: missing $ENV_FILE" >&2
  exit 2
fi
if [[ ! -x "$SCAN_LOOP" ]]; then
  echo "larp-scan-launchd: missing executable $SCAN_LOOP (run service/install.sh)" >&2
  exit 2
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

exec "$SCAN_LOOP"
