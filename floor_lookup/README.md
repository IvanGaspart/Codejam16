# floor-lookup

Predict which **Trottier** floor you are on from the Wi-Fi BSSID you are associated with.

Includes cross-platform AP detection (Linux / macOS / Windows) in `floor_lookup.wifi` — no separate `ap_detect` dependency.

```python
>>> from floor_lookup import predict_floor, get_current_ap
>>> get_current_ap()
{'bssid': '44:48:c1:a4:39:50', 'ssid': 'wpa.mcgill.ca'}
>>> predict_floor()
{'known': True, 'floor': 3, 'bssid': '…', 'ssid': 'wpa.mcgill.ca', 'confidence': 'high'}
```

```bash
cd floor_lookup
uv sync
uv run predict-floor
uv run python -m floor_lookup
```

## Install

```bash
cd floor_lookup
uv sync
```

On macOS this also installs `pyobjc-framework-CoreWLAN`.

### macOS: build the helper (once)

On macOS 14+, a small helper app is needed to read the BSSID (Location Services). Build after cloning (needs Xcode CLT):

```bash
xcode-select --install     # skip if already installed
bash helper/build.sh       # creates src/floor_lookup/_bin/WifiInfo.app
uv run python -c "from floor_lookup import authorize; print(authorize())"
```

Allow the one-time Location Services prompt for **WifiInfo**. After that, `get_current_ap()` / `predict-floor` work silently.

Linux and Windows need no build step.

## CLI

```bash
# predict current floor from the connected AP
uv run predict-floor

# survey walk (Linux): append visible APs at a floor into ../data/survey/
uv run collect-aps --floor 3
```

Raw survey CSVs under `/data/` are gitignored (see repo `.gitignore`). The packaged lookup lives in `src/floor_lookup/data/`.

## Data

| File | Role |
|------|------|
| `src/floor_lookup/data/floor_ap_lookup.csv` | BSSID → floor with confidence / RSSI margin |
| `src/floor_lookup/data/trottier.csv` | Server-ready `bssid,floor` (RQ-03) |

## Layout

| Module | Role |
|--------|------|
| `wifi` | Connected BSSID/SSID (`get_current_ap`, `authorize`) |
| `predict` | Floor lookup + `predict-floor` CLI |
| `collect` | Linux survey walk CLI |
