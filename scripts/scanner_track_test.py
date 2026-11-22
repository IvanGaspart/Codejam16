#!/usr/bin/env python3
"""Test-only scanner tracker: polls Postgres via psql and maps each Scanner's latest batch.

Serves a Leaflet map on http://localhost:8765 (not the larpscan frontend) that re-runs the
query every few seconds. Each Scanner is drawn with its close and far discs, labelled by the last
4 hex digits of its scanner_id. The page re-implements BusynessCalculator's estimate in the
browser so the constants can be tweaked live against the current readings.

Setup: add Render's "PSQL Command" to ~/.config/larp-scan/env, single-quoted (the launchd
wrapper sources that file with bash):

    LARP_PSQL_COMMAND='PGPASSWORD=... psql -h ....render.com -U ... dbname'

Then run:
    python3 scripts/scanner_track_test.py
"""

import csv
import io
import json
import os
import re
import subprocess
import sys
import threading
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

PORT = 8765
REFRESH_SECONDS = 3
# Scanners heard from within this many seconds are shown; the WINDOW slider decides which count.
LOOKBACK_SECONDS = 3600

REPO = Path(__file__).resolve().parent.parent
RESOURCES = REPO / "server/src/main/resources"
ENV_FILE = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config")) / "larp-scan/env"

QUERY = f"""
SELECT DISTINCT ON (b.scanner_id)
       b.scanner_id, ap.floor, b.latitude, b.longitude, b.received_at, b.bssid, b.rssis,
       EXTRACT(EPOCH FROM (now() - b.received_at))::int AS age_s
FROM batch b
LEFT JOIN access_point ap ON ap.bssid = b.bssid
WHERE b.received_at > now() - interval '{LOOKBACK_SECONDS} seconds'
ORDER BY b.scanner_id, b.received_at DESC, b.id DESC;
"""


def read_psql_command():
    """LARP_PSQL_COMMAND from the environment, else from the scanner env file."""
    if os.environ.get("LARP_PSQL_COMMAND"):
        return os.environ["LARP_PSQL_COMMAND"]
    if ENV_FILE.exists():
        for line in ENV_FILE.read_text().splitlines():
            key, sep, value = line.strip().removeprefix("export ").partition("=")
            if sep and key.strip() == "LARP_PSQL_COMMAND":
                value = value.strip()
                if len(value) >= 2 and value[0] == value[-1] and value[0] in "'\"":
                    value = value[1:-1]
                return value
    return ""


def query_scanners(psql_command):
    """Runs QUERY through the psql command and returns one dict per Scanner."""
    # The SQL goes through an env var so the configured command needs no extra quoting.
    result = subprocess.run(
        f'{psql_command} --csv -X -c "$LARP_TRACK_SQL"',
        shell=True,
        capture_output=True,
        text=True,
        timeout=20,
        env={**os.environ, "LARP_TRACK_SQL": QUERY},
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or f"psql exited {result.returncode}")

    scanners = []
    for row in csv.DictReader(io.StringIO(result.stdout)):
        rssis = row["rssis"].strip("{}")
        scanners.append({
            "id": row["scanner_id"],
            "label": row["scanner_id"].replace("-", "")[-4:],
            "floor": int(row["floor"]) if row["floor"] else None,
            "lat": float(row["latitude"]) if row["latitude"] else None,
            "lon": float(row["longitude"]) if row["longitude"] else None,
            "receivedAt": row["received_at"],
            "bssid": row["bssid"],
            "rssis": [int(r) for r in rssis.split(",")] if rssis else [],
            "ageS": int(row["age_s"]),
        })
    return scanners


def load_reference():
    """Server defaults from application.yml plus the floors and AP positions it ships with."""
    yml = (RESOURCES / "application.yml").read_text()
    defaults = {
        key: float(value)
        for key, value in re.findall(r"^\s*([\w-]+):\s*\$\{LARP_\w+:(-?[\d.]+)\}", yml, re.M)
    }
    with (RESOURCES / "data/floors.csv").open() as f:
        floors = [{"floor": int(r["floor"]), "capacity": int(r["capacity"])}
                  for r in csv.DictReader(f)]
    with (RESOURCES / "data/ap_gps_trottier.csv").open() as f:
        aps = [{"lat": float(r["latitude"]), "lon": float(r["longitude"]),
                "floor": int(r["floor_number"])}
               for r in csv.DictReader(f, skipinitialspace=True)]
    return {"defaults": defaults, "floors": floors, "aps": aps}


PAGE = r"""<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>Scanner Track Test</title>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
  html, body { height: 100%; margin: 0; font: 13px system-ui, sans-serif; color: #222; }
  body { display: flex; }
  #panel { width: 430px; flex: none; overflow-y: auto; padding: 12px; box-sizing: border-box;
           border-right: 1px solid #ccc; background: #fafafa; }
  #map { flex: 1; }
  h2 { font-size: 13px; margin: 16px 0 6px; text-transform: uppercase; letter-spacing: .04em;
       color: #555; }
  #status .err { color: #c00; white-space: pre-wrap; }
  table { border-collapse: collapse; width: 100%; }
  th, td { padding: 3px 4px; text-align: right; white-space: nowrap; }
  th { font-weight: 600; color: #555; border-bottom: 1px solid #ccc; }
  td:first-child, th:first-child { text-align: left; }
  tr.off td { opacity: .45; }
  .sw { display: inline-block; width: 11px; height: 11px; border-radius: 50%;
        vertical-align: -1px; margin: 0 4px; }
  .cap { width: 44px; }
  .up { color: #b3261e; } .down { color: #1e6fb3; }
  .note { color: #666; font-size: 12px; margin-top: 4px; }
  .param { display: grid; grid-template-columns: 1fr 64px; gap: 2px 8px; margin-bottom: 8px; }
  .param label { font-weight: 600; }
  .param input[type=number] { width: 64px; box-sizing: border-box; }
  .param .tw { display: flex; gap: 6px; align-items: center; grid-column: 1 / 3; }
  .param input[type=range] { flex: 1; }
  .param .val { width: 64px; text-align: right; font-variant-numeric: tabular-nums; }
  .param .val.changed { color: #b3261e; font-weight: 600; }
  .seg button { padding: 4px 10px; border: 1px solid #999; background: #fff; cursor: pointer; }
  .seg button.on { background: #333; color: #fff; }
  .lbl { background: none; border: none; }
  .lbl div { transform: translate(-50%, -50%); white-space: nowrap; text-align: center;
             font-size: 11px; line-height: 1.25; color: #111;
             text-shadow: 0 0 3px #fff, 0 0 3px #fff, 0 0 3px #fff; }
  .lbl b { font-size: 13px; }
  .lbl .ov { color: #b3261e; }
</style>
</head>
<body>
<div id="panel">
  <div id="status">loading…</div>

  <h2>Units</h2>
  <div class="seg"><button data-mode="devices" class="on">By device</button><button
    data-mode="people">By person</button></div>

  <h2>Floors (legend · filter · readings with tweaked constants)</h2>
  <table>
    <thead><tr><th>Floor</th><th>Cap</th><th>Scnr</th><th>Raw</th><th>Overlap</th>
      <th>Estimate</th><th>%</th></tr></thead>
    <tbody id="floors"></tbody>
  </table>
  <div class="note">Raw = Σ weight of positioned Scanners; Overlap = removed as double-counted;
    Estimate = max(Raw − Overlap, largest positionless Scanner). Arrows show the change from
    the current constants. Unsmoothed, unlike the API.</div>
  <div class="note" id="positionless"></div>

  <h2>Constants</h2>
  <div class="note" style="margin-bottom:8px">Box = current value in production (edit to
    match Render). Slider = what-if; the map and readings use the slider values.
    <button id="reset">Reset sliders</button></div>
  <div id="params"></div>
</div>
<div id="map"></div>
<script>
const REFRESH_MS = __REFRESH__ * 1000;
const EARTH_RADIUS_M = 6371000;
const GRID_STEP_M = 0.25;
const PARAMS = [
  {key: 'window-seconds', label: 'WINDOW (s)', min: 15, max: 600, step: 5},
  {key: 'k', label: 'K (devices per person)', min: 0.5, max: 6, step: 0.1},
  {key: 'close-rssi', label: 'CLOSE_RSSI (dBm)', min: -90, max: -40, step: 1},
  {key: 'close-d', label: 'CLOSE_D (m)', min: 1, max: 20, step: 0.1},
  {key: 'far-rssi', label: 'FAR_RSSI (dBm)', min: -100, max: -50, step: 1},
  {key: 'far-d', label: 'FAR_D (m)', min: 5, max: 60, step: 0.5},
  {key: 'far-downscale-factor', label: 'FAR_DOWNSCALE_FACTOR', min: 0, max: 1.5, step: 0.05},
];
const FLOOR_COLORS = ['#e6194b', '#3cb44b', '#4363d8', '#f58231', '#911eb4', '#0fa3b1',
                      '#f032e6', '#9a6324'];
const UNKNOWN = 'unknown';

// ── Estimate: mirrors BusynessCalculator ─────────────────────────────────────────────────
function strata(s, p) {
  let c = 0, b = 0;
  for (const r of s.rssis) {
    if (r >= p['close-rssi']) c++;
    else if (r >= p['far-rssi']) b++;
  }
  return {c, b, w: c + p['far-downscale-factor'] * b};
}

function project(lat, lon, lat0, lon0) {
  const rad = Math.PI / 180;
  return [(lon - lon0) * rad * Math.cos(lat0 * rad) * EARTH_RADIUS_M, (lat - lat0) * rad * EARTH_RADIUS_M];
}

/** Σ over positioned Scanners of w_s × Σ(ρ_s/k) / Σ ρ_s; records each one's kept share. */
function union(pos, p, st) {
  if (!pos.length) return 0;
  const closeD = p['close-d'], farD = p['far-d'], alpha = p['far-downscale-factor'];
  const xy = pos.map(s => project(s.lat, s.lon, pos[0].lat, pos[0].lon));
  const cells = Math.ceil(2 * farD / GRID_STEP_M);
  let total = 0;
  pos.forEach((s, i) => {
    const {c, b, w} = st[s.id];
    const closeDensity = c / (Math.PI * closeD * closeD);
    const farDensity = alpha * b / (Math.PI * (farD * farD - closeD * closeD));
    let num = 0, den = 0;
    for (let a = 0; a < cells; a++) {
      const dx = -farD + (a + 0.5) * GRID_STEP_M;
      for (let j = 0; j < cells; j++) {
        const dy = -farD + (j + 0.5) * GRID_STEP_M;
        const r2 = dx * dx + dy * dy;
        if (r2 > farD * farD) continue;
        const rho = r2 <= closeD * closeD ? closeDensity : farDensity;
        if (rho === 0) continue;
        let k = 0;
        for (const [tx, ty] of xy) {
          const ex = xy[i][0] + dx - tx, ey = xy[i][1] + dy - ty;
          if (ex * ex + ey * ey <= farD * farD) k++;
        }
        num += rho / k;
        den += rho;
      }
    }
    st[s.id].kept = den > 0 ? w * num / den : 0;
    total += st[s.id].kept;
  });
  return total;
}

function compute(p, scanners, floorIds, capacity) {
  const st = {}, byFloor = {};
  for (const s of scanners) {
    st[s.id] = {...strata(s, p), kept: null, live: s.floor != null && s.ageS <= p['window-seconds']};
    if (st[s.id].live) (byFloor[s.floor] ??= []).push(s);
  }
  const floors = {};
  for (const f of floorIds) {
    const list = byFloor[f] || [];
    const pos = list.filter(s => s.lat != null);
    let positionless = 0;
    for (const s of list) if (s.lat == null) positionless = Math.max(positionless, st[s.id].w);
    const raw = pos.reduce((a, s) => a + st[s.id].w, 0);
    const u = union(pos, p, st);
    const devices = Math.max(u, positionless);
    const people = devices / p.k;
    const cap = capacity[f] || 0;
    floors[f] = {scanners: list.length, raw, overlap: raw - u, positionless, devices, people,
                 pct: list.length && cap > 0 ? Math.min(100, Math.round(100 * people / cap)) : 0};
  }
  return {st, floors};
}
// ─────────────────────────────────────────────────────────────────────────────────────────

const store = {
  get(key, fallback) { try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; } },
  set(key, value) { try { localStorage.setItem(key, JSON.stringify(value)); } catch {} },
};

let ref, scanners = [], mode = 'devices', current = {}, tweak = {}, capacity = {};
let hidden = new Set(store.get('scannerTrack.hidden', []));
let fitted = false, renderQueued = false;

const map = L.map('map').setView([45.5073, -73.5792], 19);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 22, maxNativeZoom: 19, attribution: '&copy; OpenStreetMap'
}).addTo(map);
const apLayer = L.layerGroup().addTo(map);
const farLayer = L.layerGroup().addTo(map);
const closeLayer = L.layerGroup().addTo(map);
const labelLayer = L.layerGroup().addTo(map);

const floorIds = () => {
  const ids = new Set(ref.floors.map(f => f.floor));
  for (const s of scanners) if (s.floor != null) ids.add(s.floor);
  return [...ids].sort((a, b) => a - b);
};
const floorColor = f => f == null ? '#777' : FLOOR_COLORS[floorIds().indexOf(f) % FLOOR_COLORS.length];
const visible = f => !hidden.has(String(f ?? UNKNOWN));
const unit = (devices, p) => mode === 'people' ? devices / p.k : devices;
const fmt = (devices, p) => {
  const v = unit(devices, p);
  return (Math.abs(v - Math.round(v)) < 1e-9 ? String(Math.round(v)) : v.toFixed(1)) +
         (mode === 'people' ? ' ppl' : '');
};
const arrow = (from, to, digits = 1) => {
  const d = to - from;
  if (Math.abs(d) < 0.05) return '';
  return ` <span class="${d > 0 ? 'up' : 'down'}">(${d > 0 ? '+' : ''}${d.toFixed(digits)})</span>`;
};

function buildParams() {
  const el = document.getElementById('params');
  el.innerHTML = '';
  for (const P of PARAMS) {
    const row = document.createElement('div');
    row.className = 'param';
    row.innerHTML = `<label>${P.label}</label><input type="number" step="${P.step}">
      <div class="tw"><input type="range" min="${P.min}" max="${P.max}" step="${P.step}">
      <span class="val"></span></div>`;
    const [box, slider] = row.querySelectorAll('input');
    const val = row.querySelector('.val');
    const show = () => {
      val.textContent = tweak[P.key];
      val.classList.toggle('changed', tweak[P.key] !== current[P.key]);
    };
    box.value = current[P.key];
    slider.value = tweak[P.key];
    box.addEventListener('change', () => {
      const v = parseFloat(box.value);
      if (Number.isNaN(v)) return;
      current[P.key] = tweak[P.key] = v;
      slider.value = v;
      store.set('scannerTrack.current', current);
      show(); queueRender();
    });
    slider.addEventListener('input', () => {
      tweak[P.key] = parseFloat(slider.value);
      show(); queueRender();
    });
    show();
    el.appendChild(row);
  }
}

document.getElementById('reset').onclick = () => { tweak = {...current}; buildParams(); queueRender(); };
document.querySelectorAll('.seg button').forEach(btn => btn.onclick = () => {
  mode = btn.dataset.mode;
  document.querySelectorAll('.seg button').forEach(b => b.classList.toggle('on', b === btn));
  queueRender();
});

function queueRender() {
  if (renderQueued) return;
  renderQueued = true;
  requestAnimationFrame(() => { renderQueued = false; render(); });
}

function render() {
  const ids = floorIds();
  const base = compute(current, scanners, ids, capacity);
  const now = compute(tweak, scanners, ids, capacity);

  // Floor table: legend, filter, readings.
  const tbody = document.getElementById('floors');
  tbody.innerHTML = '';
  const rows = ids.map(f => [String(f), f]);
  if (scanners.some(s => s.floor == null)) rows.push([UNKNOWN, null]);
  for (const [key, f] of rows) {
    const tr = document.createElement('tr');
    tr.classList.toggle('off', !visible(f));
    const sw = `<label><input type="checkbox" ${visible(f) ? 'checked' : ''}>` +
               `<span class="sw" style="background:${floorColor(f)}"></span>`;
    if (f == null) {
      const n = scanners.filter(s => s.floor == null).length;
      tr.innerHTML = `<td>${sw}unknown BSSID</label></td><td colspan="6" style="text-align:left">` +
                     `${n} scanner(s), not counted</td>`;
    } else {
      const b = base.floors[f], t = now.floors[f];
      tr.innerHTML = `<td>${sw}${f}</label></td>` +
        `<td><input class="cap" type="number" min="0" value="${capacity[f] ?? 0}"></td>` +
        `<td>${t.scanners}${arrow(b.scanners, t.scanners, 0)}</td>` +
        `<td>${fmt(t.raw, tweak)}</td>` +
        `<td>${t.overlap > 0.005 ? '−' + fmt(t.overlap, tweak) : '0'}</td>` +
        `<td><b>${fmt(t.devices, tweak)}</b>${arrow(unit(b.devices, current), unit(t.devices, tweak))}</td>` +
        `<td><b>${t.scanners ? t.pct + '%' : '–'}</b>${arrow(b.pct, t.pct, 0)}</td>`;
      tr.querySelector('.cap').addEventListener('change', e => {
        capacity[f] = parseInt(e.target.value, 10) || 0;
        store.set('scannerTrack.capacity', capacity);
        queueRender();
      });
    }
    tr.querySelector('input[type=checkbox]').addEventListener('change', e => {
      e.target.checked ? hidden.delete(key) : hidden.add(key);
      store.set('scannerTrack.hidden', [...hidden]);
      queueRender();
    });
    tbody.appendChild(tr);
  }

  const unplaced = scanners.filter(s => s.lat == null && visible(s.floor));
  document.getElementById('positionless').innerHTML = unplaced.length
    ? '<b>No position:</b> ' + unplaced.map(s => {
        const x = now.st[s.id];
        const used = x.live && now.floors[s.floor] && x.w > 0 && x.w === now.floors[s.floor].devices;
        return `${s.label} (floor ${s.floor ?? '?'}, w ${fmt(x.w, tweak)}, ${s.ageS}s` +
               `${x.live ? '' : ', stale'}${used ? ', sets floor max' : ''})`;
      }).join('; ')
    : '';

  // Map, drawn with the slider values.
  for (const layer of [apLayer, farLayer, closeLayer, labelLayer]) layer.clearLayers();
  for (const ap of ref.aps) {
    if (!visible(ap.floor)) continue;
    L.circleMarker([ap.lat, ap.lon], {radius: 3, color: floorColor(ap.floor), weight: 1, fillOpacity: 0.4})
      .bindTooltip('AP floor ' + ap.floor).addTo(apLayer);
  }
  const alpha = tweak['far-downscale-factor'];
  const points = [];
  for (const s of scanners) {
    if (s.lat == null || !visible(s.floor)) continue;
    const x = now.st[s.id];
    const color = x.live ? floorColor(s.floor) : '#999';
    const center = [s.lat, s.lon];
    points.push(center);
    L.circle(center, {radius: tweak['far-d'], color, weight: 1.5, dashArray: '6 5',
                      fillColor: color, fillOpacity: x.live ? 0.07 : 0.03}).addTo(farLayer);
    L.circle(center, {radius: tweak['close-d'], color, weight: 2, fillColor: color,
                      fillOpacity: x.live ? 0.3 : 0.1})
      .bindPopup(`<b>${s.label}</b> ${s.id}<br>floor ${s.floor ?? '? (unknown BSSID)'} · ` +
                 `${s.ageS}s ago${x.live ? '' : ' (stale, not counted)'}<br>bssid ${s.bssid}<br>` +
                 `${s.rssis.length} sightings: ${s.rssis.join(', ')}`)
      .addTo(closeLayer);

    let centre = `<b>${s.label}</b><br>close ${fmt(x.c, tweak)}`;
    if (!x.live) centre += '<br>stale';
    else if (x.kept != null) {
      centre += `<br>kept ${fmt(x.kept, tweak)} / ${fmt(x.w, tweak)}`;
      if (x.w - x.kept > 0.005) centre += `<br><span class="ov">−${fmt(x.w - x.kept, tweak)} overlap</span>`;
    }
    L.marker(center, {icon: L.divIcon({className: 'lbl', iconSize: null, html: `<div>${centre}</div>`}),
                      interactive: false}).addTo(labelLayer);
    const edge = [s.lat + tweak['far-d'] / EARTH_RADIUS_M * 180 / Math.PI, s.lon];
    L.marker(edge, {icon: L.divIcon({className: 'lbl', iconSize: null, html:
      `<div>${s.label} far ${fmt(alpha * x.b, tweak)} <span style="color:#666">(${x.b} × ${alpha})</span></div>`}),
      interactive: false}).addTo(labelLayer);
  }
  if (!fitted && points.length) { map.fitBounds(points, {maxZoom: 20, padding: [60, 60]}); fitted = true; }
}

async function refresh() {
  const status = document.getElementById('status');
  try {
    const res = await fetch('/scanners.json');
    const body = await res.json();
    if (!res.ok) throw new Error(body.error);
    scanners = body;
    status.innerHTML = `<b>${scanners.length}</b> scanner(s) in the last hour · updated ` +
                       new Date().toLocaleTimeString();
    queueRender();
  } catch (e) {
    status.innerHTML = '<span class="err">' + e.message + '</span>';
  }
}

fetch('/reference.json').then(r => r.json()).then(r => {
  ref = r;
  current = {...r.defaults, ...store.get('scannerTrack.current', {})};
  tweak = {...current};
  capacity = Object.fromEntries(r.floors.map(f => [f.floor, f.capacity]));
  Object.assign(capacity, store.get('scannerTrack.capacity', {}));
  buildParams();
  refresh();
  setInterval(refresh, REFRESH_MS);
});
</script>
</body>
</html>
""".replace("__REFRESH__", str(REFRESH_SECONDS))


def make_handler(psql_command, reference):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path == "/":
                self._send(200, "text/html", PAGE.encode())
            elif self.path == "/reference.json":
                self._send(200, "application/json", json.dumps(reference).encode())
            elif self.path == "/scanners.json":
                try:
                    body, status = query_scanners(psql_command), 200
                except Exception as e:  # surfaced on the page rather than crashing the server
                    body, status = {"error": str(e)}, 500
                self._send(status, "application/json", json.dumps(body).encode())
            else:
                self._send(404, "text/plain", b"not found")

        def _send(self, status, content_type, body):
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *args):
            pass

    return Handler


def main():
    psql_command = read_psql_command()
    if not psql_command.strip():
        sys.exit(
            f"No LARP_PSQL_COMMAND found. Add Render's PSQL command to {ENV_FILE}, single-quoted:\n"
            "  LARP_PSQL_COMMAND='PGPASSWORD=... psql -h ....render.com -U ... dbname'"
        )
    server = ThreadingHTTPServer(("127.0.0.1", PORT), make_handler(psql_command, load_reference()))
    url = f"http://localhost:{PORT}"
    print(f"Serving scanner map on {url} (refresh every {REFRESH_SECONDS}s, Ctrl-C to stop)")
    threading.Timer(0.5, webbrowser.open, args=(url,)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
