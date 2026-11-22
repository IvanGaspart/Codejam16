#!/usr/bin/env bash
# Install scan-loop as a user daemon: systemd (Linux) or LaunchAgent (macOS). RQ-08.
set -euo pipefail

SERVICE_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_EXAMPLE="$SERVICE_DIR/deploy/larp-scan.env.example"
CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/larp-scan"
ENV_FILE="$CONFIG_DIR/env"
OS="$(uname -s)"
LOCAL_BIN="${HOME}/.local/bin"

install_cli_and_env() {
  cd "$SERVICE_DIR"
  echo "install.sh: installing scanner CLI to ~/.local/bin …"
  # --reinstall: rebuild the wheel so a freshly built WifiInfo.app isn't served from uv's cache
  uv tool install --force --reinstall .

  mkdir -p "$CONFIG_DIR" "$LOCAL_BIN"

  if [[ ! -f "$ENV_FILE" ]]; then
    cp "$ENV_EXAMPLE" "$ENV_FILE"
    chmod 600 "$ENV_FILE"
    echo "install.sh: wrote $ENV_FILE — edit LARP_SERVER_URL and LARP_API_KEY before relying on posts"
  else
    echo "install.sh: keeping existing $ENV_FILE"
  fi
}

install_linux() {
  local unit_src="$SERVICE_DIR/deploy/larp-scan.service"
  local user_unit_dir="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
  local unit_dst="$user_unit_dir/larp-scan.service"

  if ! command -v systemctl >/dev/null 2>&1; then
    echo "install.sh: systemctl is required on Linux" >&2
    exit 1
  fi

  mkdir -p "$user_unit_dir"
  cp "$unit_src" "$unit_dst"
  echo "install.sh: installed $unit_dst"

  systemctl --user daemon-reload
  loginctl enable-linger "$USER"
  systemctl --user enable --now larp-scan.service

  echo "install.sh: larp-scan.service enabled and started"
  echo "  status:  systemctl --user status larp-scan.service"
  echo "  logs:    journalctl --user -u larp-scan.service -f"
}

install_darwin() {
  local wrapper_src="$SERVICE_DIR/deploy/larp-scan-launchd.sh"
  local wrapper_dst="$LOCAL_BIN/larp-scan-launchd"
  local plist_src="$SERVICE_DIR/deploy/com.codejam16.larp-scan.plist"
  local agents_dir="${HOME}/Library/LaunchAgents"
  local plist_dst="$agents_dir/com.codejam16.larp-scan.plist"
  local label="com.codejam16.larp-scan"
  local domain="gui/$(id -u)"

  if ! command -v launchctl >/dev/null 2>&1; then
    echo "install.sh: launchctl is required on macOS" >&2
    exit 1
  fi

  # The daemon launches the WifiInfo.app copy inside the uv tool env, so authorize that one.
  # It is the only copy the daemon runs: a second build under the same bundle ID makes
  # locationd drop the grant on every launch and re-prompt every scan.
  local tool_python
  tool_python="$(uv tool dir)/scanner/bin/python"
  echo "install.sh: authorizing installed WifiInfo.app (allow the Location Services prompt if shown) …"
  local auth
  auth="$("$tool_python" -c 'from ap_detect import authorize; print(authorize())')"
  echo "install.sh: authorize() -> $auth"
  case "$auth" in
    helper-missing)
      echo "install.sh: WifiInfo.app is missing from the installed ap_detect package, failed to launch, or timed out (answer the Location prompt within 90 s); check ../ap_detect/src/ap_detect/_bin/WifiInfo.app and re-run install.sh" >&2
      exit 1 ;;
  esac

  cp "$wrapper_src" "$wrapper_dst"
  chmod +x "$wrapper_dst"
  echo "install.sh: installed $wrapper_dst"

  mkdir -p "$agents_dir"
  cp "$plist_src" "$plist_dst"
  echo "install.sh: installed $plist_dst"

  # bootout is asynchronous; bootstrapping before the old instance is gone fails with "5: Input/output error"
  launchctl bootout "$domain/$label" 2>/dev/null || true
  local i
  for i in {1..50}; do
    launchctl print "$domain/$label" >/dev/null 2>&1 || break
    sleep 0.2
  done
  # enable before bootstrap: a label left disabled (launchctl disable) also fails with "5: Input/output error"
  launchctl enable "$domain/$label"
  launchctl bootstrap "$domain" "$plist_dst"
  launchctl kickstart -k "$domain/$label" 2>/dev/null || true

  echo "install.sh: $label bootstrapped"
  echo "  status:  launchctl print $domain/$label"
  echo "  logs:    tail -f /tmp/com.codejam16.larp-scan.out.log /tmp/com.codejam16.larp-scan.err.log"
  echo "  restart: launchctl kickstart -k $domain/$label"
  echo "  stop:    launchctl bootout $domain/$label"
}

if [[ "$OS" != "Linux" && "$OS" != "Darwin" ]]; then
  echo "install.sh: unsupported OS $OS (need Linux or macOS)" >&2
  exit 1
fi

if ! command -v uv >/dev/null 2>&1; then
  echo "install.sh: uv is required on PATH" >&2
  exit 1
fi

# Everything goes under $HOME (uv tool env, ~/.local/bin, LaunchAgents / systemd --user).
# Running with sudo leaves root-owned files there, and every later (correct, unprivileged)
# re-run then dies with "Permission denied" inside `uv tool install --force`.
if [[ "$(id -u)" -eq 0 ]]; then
  echo "install.sh: do not run with sudo; it installs a *user* daemon into $HOME" >&2
  exit 1
fi

tool_env="$(uv tool dir)/scanner"
foreign=()
for p in "$tool_env" "$LOCAL_BIN/scan-loop" "$LOCAL_BIN/predict-floor" "$LOCAL_BIN/collect-aps" "$LOCAL_BIN/larp-scan-launchd"; do
  [[ -e "$p" || -L "$p" ]] || continue
  # find without -L stats the symlink itself, so a root-owned symlink is caught too
  if [[ -n "$(find "$p" ! -user "$(id -un)" 2>/dev/null | head -1)" ]]; then
    foreign+=("$p")
  fi
done
if [[ ${#foreign[@]} -gt 0 ]]; then
  echo "install.sh: these paths contain files not owned by $(id -un) (left over from a sudo run?):" >&2
  printf '  %s\n' "${foreign[@]}" >&2
  echo "install.sh: remove them and re-run:  sudo rm -rf ${foreign[*]}" >&2
  exit 1
fi

AP_DETECT_DIR="$SERVICE_DIR/../ap_detect"
if [[ "$OS" == "Darwin" && ! -d "$AP_DETECT_DIR/src/ap_detect/_bin/WifiInfo.app" ]]; then
  # _bin/ is gitignored, so fresh clones don't have the helper; it must exist before the ap-detect wheel is built
  echo "install.sh: WifiInfo.app not built yet; running ap_detect/helper/build.sh (needs Xcode CLT) …"
  bash "$AP_DETECT_DIR/helper/build.sh"
fi

install_cli_and_env

case "$OS" in
  Linux) install_linux ;;
  Darwin) install_darwin ;;
esac
