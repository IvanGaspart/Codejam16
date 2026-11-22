#!/usr/bin/env bash
# Install scan-loop as a systemd user service (Linux / RQ-08).
set -euo pipefail

SERVICE_DIR="$(cd "$(dirname "$0")" && pwd)"
UNIT_SRC="$SERVICE_DIR/deploy/larp-scan.service"
ENV_EXAMPLE="$SERVICE_DIR/deploy/larp-scan.env.example"
CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/larp-scan"
ENV_FILE="$CONFIG_DIR/env"
USER_UNIT_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
UNIT_DST="$USER_UNIT_DIR/larp-scan.service"

if [[ "$(uname -s)" != "Linux" ]]; then
  echo "install.sh: systemd user service is Linux-only (got $(uname -s))" >&2
  exit 1
fi

if ! command -v uv >/dev/null 2>&1; then
  echo "install.sh: uv is required on PATH" >&2
  exit 1
fi

if ! command -v systemctl >/dev/null 2>&1; then
  echo "install.sh: systemctl is required" >&2
  exit 1
fi

cd "$SERVICE_DIR"
echo "install.sh: installing scanner CLI to ~/.local/bin …"
uv tool install --force .

mkdir -p "$CONFIG_DIR" "$USER_UNIT_DIR"

if [[ ! -f "$ENV_FILE" ]]; then
  cp "$ENV_EXAMPLE" "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  echo "install.sh: wrote $ENV_FILE — edit LARP_SERVER_URL and LARP_API_KEY before relying on posts"
else
  echo "install.sh: keeping existing $ENV_FILE"
fi

cp "$UNIT_SRC" "$UNIT_DST"
echo "install.sh: installed $UNIT_DST"

systemctl --user daemon-reload
loginctl enable-linger "$USER"
systemctl --user enable --now larp-scan.service

echo "install.sh: larp-scan.service enabled and started"
echo "  status:  systemctl --user status larp-scan.service"
echo "  logs:    journalctl --user -u larp-scan.service -f"
