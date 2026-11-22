# Building & AP Mapping Data

Static CSV datasets loaded at server startup by `ReferenceDataLoader` (see `docs/SPEC.md`). On every
start the `floor` and `access_point` tables are emptied and reloaded from these files in one
transaction; a malformed row stops startup with the file and line number.

- `floors.csv` — columns: `floor,capacity` (seat count per floor in Trottier)
- `trottier.csv` — columns: `bssid,floor` (Wi-Fi AP BSSID mapped to its strongest floor). BSSIDs are
  lowercased on load, and every floor used here must appear in `floors.csv`.

Both files need the header line exactly as above. Comments are not supported.

**Placeholder:** the capacities in `floors.csv` (100 for every floor) are placeholders until the real
seat counts are collected (issue #48).
