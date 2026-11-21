# LARP-scan Backlog

Source of truth for populating the GitHub Project. Requirements are defined in [`SPEC.md`](SPEC.md).

## Conventions

### Labels

- **Slice:** `slice:db`, `slice:rest`, `slice:frontend`, `slice:hosting`, `slice:scanner`, `slice:data`, `slice:logic`
- **Category:** `type:requirement`, `type:feature`, `type:task`, `type:spike`, `type:docs`, plus GitHub's default `bug` and `enhancement`

### Structure

- **Milestones:** `MVP`, `Stretch 1`
- **Requirement issues:** one per RQ, titled `RQ-NN: …`, labelled `type:requirement`
- **Task issues:** each is a native sub-issue of its **Parent** RQ. Other RQs it covers are listed as `Relates to #n` in its body.
- **Board columns:** Backlog / Todo / In Progress / Review / Done

## Requirement issues

| Issue | Slices | Assignee | Milestone |
|---|---|---|---|
| RQ-01: Scanner sends batches | scanner | zachmchuang | MVP |
| RQ-02: Busyness from distinct addresses in window | logic, db | IvanGaspart | MVP |
| RQ-03: AP → Floor mapping | data, db | zachmchuang | MVP |
| RQ-04: REST API | rest | jacob-sauve | MVP |
| RQ-05: Only non-resolvable addresses, no names | scanner | zachmchuang | MVP |
| RQ-06: Website on the internet | frontend, hosting | jacob-sauve | MVP |
| RQ-07: Human-readable results (level, smoothing, history) | logic | IvanGaspart | MVP |
| RQ-08: Scanner runs as a daemon | scanner | zachmchuang | MVP (systemd) / Stretch 1 (launchd) |
| RQ-09: Trottier only | data | zachmchuang | MVP |
| RQ-10: "No data" for uncovered Floors | logic, frontend | IvanGaspart | MVP |
| RQ-11: API key on POST | rest | jacob-sauve | MVP |
| RQ-12: Strongest-RSSI resolution across Scanners | logic | IvanGaspart | Stretch 1 |
| RQ-13: Busyness predictions | logic, frontend | IvanGaspart | Stretch 1 |
| RQ-14: Interactive building map | frontend | jacob-sauve | Stretch 1 |

## Task issues

### First

| Task | Type | Slices | Assignee | Parent | Relates to | Milestone |
|---|---|---|---|---|---|---|
| Write specification (`docs/SPEC.md`, `GLOSSARY.md`) | docs | all | IvanGaspart | RQ-04 | all | MVP |
| Populate backlog and Kanban board | docs | all | IvanGaspart | RQ-04 | all | MVP |

### DB and business logic: IvanGaspart

| Task | Type | Slices | Parent | Relates to | Milestone |
|---|---|---|---|---|---|
| JPA entities and schema (floor, access_point, batch, sighting, busyness_snapshot) | task | db | RQ-02 | RQ-03, RQ-07 | MVP |
| CSV loader for `trottier.csv` and `floors.csv` at startup | feature | db, data | RQ-03 | RQ-09 | MVP |
| Batch ingestion service (store batch and Sightings, log unknown BSSIDs) | feature | logic | RQ-02 | RQ-03, RQ-04 | MVP |
| Busyness calculation (distinct / K / capacity, levels) | feature | logic | RQ-02 | RQ-07 | MVP |
| Coverage logic (`NO_DATA`) | feature | logic | RQ-10 | RQ-02 | MVP |
| Expiry job (delete batches and Sightings older than `WINDOW`) | feature | logic, db | RQ-02 | | MVP |
| Snapshot job (every `SNAPSHOT_INTERVAL`) and smoothing | feature | logic, db | RQ-07 | | MVP |
| History query service | feature | logic | RQ-07 | RQ-04 | MVP |
| Strongest-RSSI resolution across Scanners | feature | logic | RQ-12 | RQ-02 | Stretch 1 |
| Predictions from snapshot history | feature | logic | RQ-13 | RQ-07 | Stretch 1 |

### REST, frontend and hosting: jacob-sauve

| Task | Type | Slices | Parent | Relates to | Milestone |
|---|---|---|---|---|---|
| Scaffold Spring Boot app (Initializr) in `server/` | task | rest | RQ-04 | | MVP |
| `POST /api/batches` controller and DTO validation | feature | rest | RQ-04 | RQ-01 | MVP |
| `GET /api/buildings` and `GET …/floors` | feature | rest | RQ-04 | RQ-06 | MVP |
| `GET …/floors/{floor}/history` | feature | rest | RQ-04 | RQ-07 | MVP |
| API-key filter on POST | feature | rest | RQ-11 | RQ-04 | MVP |
| CORS for the Lovable origin | task | rest | RQ-06 | RQ-04 | MVP |
| Lovable app: building card and floor stack (polling, `NO_DATA`) | feature | frontend | RQ-06 | RQ-10 | MVP |
| Lovable app: floor view with history chart | feature | frontend | RQ-06 | RQ-07 | MVP |
| Dockerfile and Render web service for Spring | task | hosting | RQ-06 | | MVP |
| Render Postgres, wired to Spring via env vars | task | hosting, db | RQ-06 | RQ-02 | MVP |
| Lovable Publish, pointed at Render API URL | task | hosting, frontend | RQ-06 | | MVP |
| Interactive building map | feature | frontend | RQ-14 | | Stretch 1 |
| Predictions UI | feature | frontend | RQ-13 | | Stretch 1 |

### Scanner and data collection: zachmchuang

| Task | Type | Slices | Parent | Relates to | Milestone |
|---|---|---|---|---|---|
| Scaffold `scanner/` package (depends on `ap_detect`) | task | scanner | RQ-01 | | MVP |
| BLE scan loop with bleak (RSSI floor, drop names) | feature | scanner | RQ-01 | RQ-05 | MVP |
| Batch builder and poster (BSSID per batch, empty batches, skip when off Wi-Fi) | feature | scanner | RQ-01 | RQ-04, RQ-11 | MVP |
| Scanner ID generation and config via env vars | task | scanner | RQ-01 | | MVP |
| systemd user unit and `install.sh` | feature | scanner | RQ-08 | | MVP |
| Walk Trottier: collect BSSIDs per floor into `trottier.csv` | task | data | RQ-03 | RQ-09 | MVP |
| Count seats per floor into `floors.csv` | task | data | RQ-03 | RQ-09 | MVP |
| Calibrate `K` with a head count on one floor | task | data | RQ-07 | | MVP |
| launchd agent and macOS permission spike | spike | scanner | RQ-08 | | Stretch 1 |
