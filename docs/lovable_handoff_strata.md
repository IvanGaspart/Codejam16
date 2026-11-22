# Lovable handoff: overlap-aware occupancy backend

The backend changed how it estimates the number of people on a Floor. **The public GET API is unchanged**, so the website needs no code changes. Only the text that explains the numbers needs updating.

## What changed on the server

- Before, when several Scanners were on one Floor, the server kept only the largest Scanner's device count.
- Now each Scanner is placed on the Floor by Wi-Fi trilateration. Its devices are split into a **close** circle (strong signal) and a **far** ring (weaker signal, weighted slightly lower). Where Scanners' circles overlap, the devices in the overlap are counted once.
- Scanners that cannot be placed (old Scanner builds, or Floors with no AP location data yet) fall back to the previous max-count rule.

## Endpoints (unchanged shapes)

| Endpoint | Response | Change |
|---|---|---|
| `GET /api/buildings` | `[{ id, name }]` | none |
| `GET /api/buildings/{buildingId}/floors` | `[{ floor, capacity, covered, scanners, estimatedPeople, busynessPct, level }]` | none to the shape. `estimatedPeople` and `busynessPct` now add up the Scanners' coverage instead of taking the max, so they can be higher when several Scanners cover different parts of a Floor. |
| `GET /api/buildings/{buildingId}/floors/{floor}/history?since=` | `[{ at, busynessPct, covered }]` | none. Points recorded before the deploy used the old rule, so expect a step in today's chart around the deploy time. |

`level` is still one of `QUIET | MODERATE | BUSY | FULL | NO_DATA`, with the same thresholds (<30, <60, <85, ≥85).

`POST /api/batches` gained an optional `aps` field, but the website never calls it.

## Requested website changes (optional, copy only)

1. Wherever the UI explains the estimate (tooltip, "how it works", or the Scanner count line), replace any wording like "based on the busiest scanner" with: *"Combined from {scanners} scanner(s); overlapping areas counted once."*
2. Keep everything else as is: polling every 15 s, the "No data" state, colours, history chart.

Do **not** add a Supabase or any other backend. Do not rename or remove any fields.
