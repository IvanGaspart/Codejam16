# Codejam16

Zach Chuang, Jacob Sauvé, Ivan Gaspart

LARP-scan — see [`docs/SPEC.md`](docs/SPEC.md) and [ADR-0002](docs/adr/0002-rssi-only-batches.md) (RSSI-only batches).

## Server

Spring Boot app in [`server/`](server/). Scanners ingest batches with:

```http
POST /api/batches
X-API-Key: <LARP_API_KEY>
Content-Type: application/json
```

```json
{
  "scannerId": "3f2b1a4c-8d9e-4b2a-9c1e-0123456789ab",
  "bssid": "44:48:c1:a4:1a:50",
  "sentAt": "2026-11-21T14:03:15Z",
  "sightings": [-67, -72, -80]
}
```

- `sightings` is an array of RSSI values in dBm (−127…0), one per distinct device heard in the interval. It may be `[]`.
- No BLE addresses, names, or per-device timestamps — only batch `sentAt` (see FX-01 / ADR-0002).
- Response: `202 Accepted`.

## Python packages

| Package | Role |
|---------|------|
| [`floor_lookup/`](floor_lookup/) | Trottier BSSID → floor (+ vendored wifi detect) |

```bash
cd floor_lookup && uv sync
uv run predict-floor
```

On macOS, see [`floor_lookup/README.md`](floor_lookup/README.md) for the WifiInfo helper build.

Raw survey walks write under `data/` (gitignored). Packaged lookup CSVs live in `floor_lookup/src/floor_lookup/data/`.
