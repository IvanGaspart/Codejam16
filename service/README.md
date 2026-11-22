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

### Daemon (systemd, Linux)

Installs `scan-loop` to `~/.local/bin` and enables a **user** unit (BlueZ/session D-Bus):

```bash
cd service
# edit secrets after first run if the example was seeded:
#   ~/.config/larp-scan/env
bash install.sh
```

```bash
systemctl --user status larp-scan.service
journalctl --user -u larp-scan.service -f
systemctl --user restart larp-scan.service
systemctl --user stop larp-scan.service
```

Re-run `bash install.sh` (or `uv tool install --force .` then `systemctl --user restart larp-scan`) after pulling scanner changes. Linger is enabled so the service can run without an interactive login.

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
  install.sh              # systemd user unit (Linux)
  deploy/                 # larp-scan.service + env example
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
