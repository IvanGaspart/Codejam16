# Codejam16

Zach Chuang, Jacob Sauvé, Ivan Gaspart

LARP-scan — see [`docs/SPEC.md`](docs/SPEC.md).

## Python packages

| Package | Role |
|---------|------|
| [`ap_detect/`](ap_detect/) | Cross-platform current Wi-Fi BSSID (`uv run python -m ap_detect`) |
| [`floor_lookup/`](floor_lookup/) | Trottier BSSID → floor lookup (`uv run predict-floor`) |

```bash
cd ap_detect && uv sync
cd ../floor_lookup && uv sync
uv run predict-floor
```

Raw survey walks write under `data/` (gitignored). Packaged lookup CSVs live in `floor_lookup/src/floor_lookup/data/`.
