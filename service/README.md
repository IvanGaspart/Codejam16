# service

LARP-scan **scanner** package: predict Trottier floor from Wi-Fi BSSID, sniff nearby BLE devices, and emit periodic updates ready for `POST /api/batches`.

```python
>>> from scanner import predict_floor, get_current_ap
>>> get_current_ap()
{'bssid': '44:48:c1:a4:39:50', 'ssid': 'wpa.mcgill.ca'}
>>> predict_floor()
{'known': True, 'floor': 3, 'bssid': '…', 'ssid': 'wpa.mcgill.ca', 'confidence': 'high'}
```

```bash
cd service
# required if the repo is under iCloud Documents (see Install)
export UV_PROJECT_ENVIRONMENT="$HOME/.venvs/larp-scan"
uv sync --group dev
uv run predict-floor
uv run scan-loop
uv run map-aps --help
uv run python -m scanner
```

## Install

On macOS this also installs `pyobjc-framework-CoreWLAN`.

**macOS + iCloud Drive: keep `.venv` out of the synced tree.** If the repo lives in an iCloud-synced folder (`~/Documents` / `~/Desktop` with Desktop & Documents Folders on), iCloud sets `UF_HIDDEN` on files inside dotted dirs like `.venv`. Python 3.13+ then logs `Skipping hidden .pth file` and editable installs fail (`ModuleNotFoundError`) even though `uv sync` succeeded. `chflags nohidden` is undone within seconds. Before `uv sync`:

```bash
export UV_PROJECT_ENVIRONMENT="$HOME/.venvs/larp-scan"   # add to ~/.zshrc
uv sync --group dev
```

Check with `ls -lO "$UV_PROJECT_ENVIRONMENT/lib/python3.13/site-packages/"*.pth` — a `hidden` column means you're still affected. Cloning outside iCloud (e.g. `~/src`) also avoids it. The daemon is unaffected: `install.sh` installs into `~/.local/share/uv/tools`.

### macOS: build the helper (once)

On macOS 14+, a small helper app is needed to read the BSSID (Location Services). Build after cloning (needs Xcode CLT):

```bash
xcode-select --install     # skip if already installed
bash helper/build.sh       # creates src/scanner/_bin/WifiInfo.app
uv run python -c "from scanner import authorize; print(authorize())"
```

Allow the one-time Location Services prompt for **WifiInfo**. After that, `get_current_ap()` / `predict-floor` work silently.

Linux and Windows need no build step.

### Daemon (systemd / launchd)

Installs `scan-loop` to `~/.local/bin` and enables a **user** daemon (systemd on Linux, LaunchAgent on macOS):

```bash
cd service
# edit secrets after first run if the example was seeded:
#   ~/.config/larp-scan/env
bash install.sh
```

Run it as yourself, **never with `sudo`**: everything it writes (`~/.local/share/uv/tools/scanner`, `~/.local/bin`, the LaunchAgent / systemd user unit) lives in your home. A `sudo` run leaves root-owned files there and every later unprivileged `install.sh` fails inside `uv tool install --force` with `failed to remove file … uv-receipt.toml: Permission denied`. `install.sh` now refuses to run as root and aborts early if it finds files it doesn't own, printing the `sudo rm -rf …` needed to clean up.

**Linux (systemd user unit)** — BlueZ/session D-Bus; linger so it can run without an interactive login:

```bash
systemctl --user status larp-scan.service
journalctl --user -u larp-scan.service -f
systemctl --user restart larp-scan.service
systemctl --user stop larp-scan.service
```

**macOS (LaunchAgent)** — the WifiInfo helper is bundled into the installed package (`src/scanner/_bin/` is gitignored, so `pyproject.toml` lists it under hatch `artifacts`). If it hasn't been built yet (e.g. a fresh clone), `install.sh` runs `helper/build.sh` first, which needs the Xcode CLT. It then runs `authorize()` against the *installed* copy, which is the one the daemon launches. Allow the Location Services prompt within 90 s. It aborts with `helper-missing` if the app isn't in the installed package, or `helper-failed` if the app is there but didn't launch or the prompt timed out. On a launch failure, the line above it (`WifiInfo.app launch failed (open exit N): …`) gives the reason from `open`, e.g. a missing arch slice or a non-executable binary. Fix that, then re-run `install.sh`. Bluetooth must be on: with it off, `scan-loop` exits with `Bluetooth device is turned off` and launchd restarts it every 5 s until you turn it on. Starts at login (`RunAtLoad`):

```bash
launchctl print gui/$(id -u)/com.codejam16.larp-scan
tail -f /tmp/com.codejam16.larp-scan.out.log /tmp/com.codejam16.larp-scan.err.log
launchctl kickstart -k gui/$(id -u)/com.codejam16.larp-scan
launchctl bootout gui/$(id -u)/com.codejam16.larp-scan
```

On macOS, BLE devices show up as peripheral UUIDs instead of MACs. This doesn't affect posting: batches carry only the RSSI array (ADR-0002), so macOS scanners post the same way Linux ones do.

Re-run `bash install.sh` after pulling scanner changes or rebuilding the helper. It is safe to re-run: it reinstalls with `uv tool install --force --reinstall .` (`--reinstall` stops uv from reusing a cached wheel that has a stale or missing helper), waits for the old LaunchAgent to unload before it bootstraps the new one, and re-enables the agent if it was turned off with `launchctl disable`.

uv may warn that `~/.local/bin` is not on your `PATH`. The daemon doesn't need it, because the plist and wrapper use absolute paths. Add it only if you want to run `scan-loop` / `predict-floor` from a shell.

## CLI

```bash
# predict current floor from the connected AP
uv run predict-floor

# continuous floor + BLE updates; POST RSSI batches (needs env below)
export LARP_SERVER_URL=http://localhost:8080
export LARP_API_KEY=dev-local-key
uv run scan-loop

# local only (no HTTP)
uv run scan-loop --no-post

# survey walk (Linux): append visible APs at a floor into ../data/survey/
uv run collect-aps --floor 3

# map labelled APs → BSSID (macOS + Linux; uses ap_detect scan)
# rebuild ap_detect WifiInfo.app first on macOS 14+: (cd ../ap_detect && bash helper/build.sh)
uv run map-aps --authorize          # macOS Location Services once
uv run map-aps trottier 3 12        # poll until Ctrl+C; writes ap_bssid_trottier.csv
uv run map-aps --interactive        # prompt building/floor, then ID per cycle
```

`map-aps` polls nearby APs; when you stop a cycle, the BSSID that was strongest most often is stored as `ID,floor_number,bssid` (foreign key to a teammate `ap_gps_<building>.csv`). Deduping (most recent wins per ID+floor) runs on process exit into `service/ap_bssid_<building>.csv`.

Every 15 s `scan-loop` POSTs FX-01.2 `BatchRequest` to `{LARP_SERVER_URL}/api/batches`:

```json
{ "scannerId", "bssid", "sentAt", "sightings": [-67, -72] }
```

`sightings` is RSSI ints only (may be `[]`). Offline ticks are skipped. Scanner UUID is created at `~/.config/larp-scan/scanner-id`.

Raw survey CSVs under `/data/` are gitignored (see repo `.gitignore`). The packaged lookup lives in `src/scanner/data/`.

## Data

| File | Role |
|------|------|
| `src/scanner/data/floor_ap_lookup.csv` | BSSID → floor with confidence / RSSI margin |
| `src/scanner/data/trottier.csv` | Server-ready `bssid,floor` (RQ-03) |

## Layout

```text
service/
  pyproject.toml          # uv project name: scanner
  uv.lock
  install.sh              # systemd (Linux) / LaunchAgent (macOS)
  deploy/                 # unit, plist, launchd wrapper, env example
  helper/                 # macOS WifiInfo.app build
  src/scanner/
    wifi.py
    predict.py
    collect.py
    ble.py
    scan_loop.py
    config.py
    poster.py
    data/
```

| Module | Role |
|--------|------|
| `wifi` | Connected BSSID/SSID (`get_current_ap`, `authorize`) |
| `predict` | Floor lookup + `predict-floor` CLI |
| `collect` | Linux survey walk CLI |
| `ble` | Bleak scan buffer (RSSI floor, no names) |
| `scan_loop` | Periodic floor + BLE + POST + `scan-loop` CLI |
| `config` | `LARP_SERVER_URL` / `LARP_API_KEY` / scanner-id file |
| `poster` | `POST /api/batches` (httpx) |
