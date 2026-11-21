# Codejam16

Zach Chuang, Jacob Sauvé, Ivan Gaspart

LARP-scan — see [`docs/SPEC.md`](docs/SPEC.md).

## Python packages

| Package | Role |
|---------|------|
| [`ap_detect/`](ap_detect/) | Shared cross-platform BSSID helper (for `scanner/` per SPEC) |
| [`service/`](service/) | Trottier BSSID → floor (+ vendored wifi detect) |

```bash
cd service && uv sync
uv run predict-floor
```

On macOS, see [`service/README.md`](service/README.md) for the WifiInfo helper build.

Raw survey walks write under `data/` (gitignored). Packaged lookup CSVs live in `service/src/floor_lookup/data/`.
