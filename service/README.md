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

## CLI

```bash
# predict current floor from the connected AP
uv run predict-floor

# continuous floor + BLE updates (RSSI floor −85 dBm, 15 s interval)
uv run scan-loop

# survey walk (Linux): append visible APs at a floor into ../data/survey/
uv run collect-aps --floor 3
```

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
  helper/                 # macOS WifiInfo.app build
  src/scanner/
    wifi.py
    predict.py
    collect.py
    ble.py
    scan_loop.py
    data/
```

| Module | Role |
|--------|------|
| `wifi` | Connected BSSID/SSID (`get_current_ap`, `authorize`) |
| `predict` | Floor lookup + `predict-floor` CLI |
| `collect` | Linux survey walk CLI |
| `ble` | Bleak scan buffer (RSSI floor, no names) |
| `scan_loop` | Periodic floor + BLE updates + `scan-loop` CLI |
