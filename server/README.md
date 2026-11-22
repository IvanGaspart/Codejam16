# server

Spring Boot 4 API for LARP-scan (Java 21). Persists scanner batches to Postgres, computes floor busyness, serves public GETs for the website.

## Local

```bash
cd server
./gradlew bootRun
```

Defaults: Postgres at `jdbc:postgresql://localhost:5432/larpscan`, `LARP_API_KEY=dev-local-key`. Override with env vars (see below).

```bash
./gradlew test
```

## Deploy (Render Docker)

1. Create a **Web Service** (Docker) and a **Postgres** instance in the same region.
2. Set **Root Directory** to `server` (this folder; uses [`Dockerfile`](Dockerfile)).
3. Set environment variables on the web service:

| Variable | Purpose |
|----------|---------|
| `PORT` | Injected by Render |
| `SPRING_DATASOURCE_URL` | JDBC URL, e.g. `jdbc:postgresql://HOST:5432/DB` |
| `SPRING_DATASOURCE_USERNAME` | Postgres user |
| `SPRING_DATASOURCE_PASSWORD` | Postgres password |
| `LARP_API_KEY` | Shared secret for `POST /api/batches` |
| `LARP_CORS_ORIGIN` | Lovable (or local) frontend origin; optional until the SPA exists |

Optional: `LARP_WINDOW_SECONDS`, `LARP_K`, `LARP_SNAPSHOT_INTERVAL_MS`, `LARP_SMOOTHING_SNAPSHOTS` (see `src/main/resources/application.yml`).

Current production-style host for scanners: `https://codejam16-ui0a.onrender.com`

## Scanner wiring

On each scanner laptop (`~/.config/larp-scan/env`):

```bash
LARP_SERVER_URL=https://codejam16-ui0a.onrender.com
LARP_API_KEY=<same as Render LARP_API_KEY>
```

Then `systemctl --user restart larp-scan.service` (or re-run `service/install.sh`).

## End-to-end check (minus frontend)

Wake / health:

```bash
curl -sS https://codejam16-ui0a.onrender.com/api/buildings
```

POST a batch (expect `202`; wrong key → `401`):

```bash
curl -sS -o /dev/null -w "%{http_code}\n" -X POST https://codejam16-ui0a.onrender.com/api/batches \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $LARP_API_KEY" \
  -d '{"scannerId":"3f2b1a4c-8d9e-4b2a-9c1e-0123456789ab","bssid":"44:48:c1:a4:1a:50","sentAt":"2026-11-22T02:00:00Z","sightings":[-67,-72]}'
```

In Render Postgres (or any SQL client):

```sql
SELECT id, scanner_id, bssid, sent_at, received_at, rssis
FROM batch
ORDER BY id DESC
LIMIT 5;
```

Live scanner: connect to Wi-Fi (BSSID required), watch `journalctl --user -u larp-scan.service -f` for successful posts, then re-run the SQL.

Optional: `GET /api/buildings/trottier/floors` after a known-AP batch within the window.
