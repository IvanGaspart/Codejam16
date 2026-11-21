# Codejam16

Zach Chuang, Jacob Sauvé, Ivan Gaspart

LARP-scan — see [`docs/SPEC.md`](docs/SPEC.md).

## Python packages

| Package | Role |
|---------|------|
| [`ap_detect/`](ap_detect/) | Shared cross-platform BSSID helper (for `scanner/` per SPEC) |
| [`floor_lookup/`](floor_lookup/) | Trottier BSSID → floor (+ vendored wifi detect) |

```bash
cd floor_lookup && uv sync
uv run predict-floor
```

On macOS, see `floor_lookup/README.md` for the WifiInfo helper build.

Raw survey walks write under `data/` (gitignored). Packaged lookup CSVs live in `floor_lookup/src/floor_lookup/data/`.
