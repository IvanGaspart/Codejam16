# floor-lookup

Predict which **Trottier** floor you are on from the Wi-Fi BSSID you are associated with.

Built as a uv package (same layout as `ap_detect`). Uses [`ap_detect`](../ap_detect) so Linux and macOS share one code path.

```python
>>> from floor_lookup import predict_floor
>>> predict_floor()
{'floor': 3, 'bssid': '44:48:c1:a4:39:50', 'ssid': 'wpa.mcgill.ca', 'confidence': 'high'}
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

This pulls in the sibling `ap_detect` package via a path dependency. On macOS, follow `ap_detect`'s README (Location Services / WifiInfo helper).

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

Rebuild the lookup from survey observations (after walking) with your analysis pipeline, then copy the CSVs into `src/floor_lookup/data/`.
