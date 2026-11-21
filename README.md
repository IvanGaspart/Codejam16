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
uv run scan-loop
```

On macOS, see [`service/README.md`](service/README.md) for the WifiInfo helper build.

Raw survey walks write under `data/` (gitignored). Packaged lookup CSVs live in `service/src/scanner/data/`.
