# Building & AP Mapping Data

Static CSV datasets loaded at server startup by `ReferenceDataLoader` (see `docs/SPEC.md`). On every
start the `floor` and `access_point` tables are emptied and reloaded from these files in one
transaction; a malformed row stops startup with the file and line number.

- `floors.csv` — columns: `floor,capacity` (seat count per floor in Trottier)
- `trottier.csv` — columns: `bssid,floor` (Wi-Fi AP BSSID mapped to its strongest floor). BSSIDs are
  lowercased on load, and every floor used here must appear in `floors.csv`.

Both files need the header line exactly as above. Comments are not supported.

Two more files give the GPS position of each Access Point radio, used to trilaterate Scanners from
the APs they hear (`aps` in `POST /api/batches`). `ApLocations` joins them in memory on
`(ID, floor_number)` at startup; rows without a partner in the other file are skipped and counted in
the log, and a malformed row stops startup.

- `ap_bssid_trottier.csv` — columns: `ID,floor_number,bssid`. An AP's SSIDs share the first five
  octets of their BSSID, so APs are matched on those (the last octet can be anything). Two APs may
  share them on different floors (e.g. `44:48:c1:a4:34` on floors 3 and 5); lookups also use the
  Scanner's floor. APs sharing them on the same floor are logged and merged into their centroid.
- `ap_gps_trottier.csv` — columns: `ID,latitude,longitude,floor_number` (WGS84 degrees).

**Placeholder:** the capacities in `floors.csv` (100 for every floor) are placeholders until the real
seat counts are collected (issue #48).
