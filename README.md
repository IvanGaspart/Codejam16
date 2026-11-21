# Codejam16

Zach Chuang, Jacob Sauvé, Ivan Gaspart

LARP-scan — see [`docs/SPEC.md`](docs/SPEC.md).

## Python packages

| Package | Role |
|---------|------|
| [`service/`](service/) | uv project `scanner`: Trottier floor + BLE scan loop |

```bash
cd service && uv sync
uv run predict-floor
export LARP_SERVER_URL=http://localhost:8080 LARP_API_KEY=dev-local-key
uv run scan-loop          # POST RSSI batches every 15s
uv run scan-loop --no-post
```

On macOS, see [`service/README.md`](service/README.md) for the WifiInfo helper build.

Raw survey walks write under `data/` (gitignored). Packaged lookup CSVs live in `service/src/scanner/data/`.
