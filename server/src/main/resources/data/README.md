# Building & AP Mapping Data

Static CSV datasets loaded at server startup (see `docs/SPEC.md`):

- `floors.csv` — columns: `floor,capacity` (seat count per floor in Trottier)
- `trottier.csv` — columns: `bssid,floor` (Wi-Fi AP BSSID mapped to its strongest floor)