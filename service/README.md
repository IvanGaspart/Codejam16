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
uv sync
uv run predict-floor
uv run scan-loop
uv run python -m scanner
```

## Install

```bash
cd service
uv sync
```

On macOS this also installs `pyobjc-framework-CoreWLAN`.

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

**Linux (systemd user unit)** — BlueZ/session D-Bus; linger so it can run without an interactive login:

```bash
systemctl --user status larp-scan.service
journalctl --user -u larp-scan.service -f
systemctl --user restart larp-scan.service
systemctl --user stop larp-scan.service
```

**macOS (LaunchAgent)** — the WifiInfo helper is bundled into the installed package (`src/scanner/_bin/` is gitignored, so `pyproject.toml` lists it under hatch `artifacts`). If it hasn't been built yet (e.g. a fresh clone), `install.sh` runs `helper/build.sh` first, which needs the Xcode CLT. It then runs `authorize()` against the *installed* copy, which is the one the daemon launches. Allow the Location Services prompt within 90 s. It aborts with `helper-missing` if the app isn't in the installed package, or `helper-failed` if the app is there but didn't launch or the prompt timed out; re-run `install.sh` in either case. Bluetooth must be on: with it off, `scan-loop` exits with `Bluetooth device is turned off` and launchd restarts it every 5 s until you turn it on. Starts at login (`RunAtLoad`):

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
```

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
