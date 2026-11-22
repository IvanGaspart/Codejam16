import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { useEffect, useRef, useState } from "react";
import type { AccessPoint, ScannerReading, ScannerStats, XRayConstants } from "@/lib/xray";
import { BUILDING_SHAPES, levelLabel, shortName, type Building, type BusynessLevel } from "@/lib/larp";

export interface MapBuilding extends Building { level: BusynessLevel; pct: number | null; trackedPct: number }

const LABEL_POSITIONS: Record<string, Pick<L.TooltipOptions, "direction" | "offset">> = {
  burnside: { direction: "bottom", offset: [0, 8] },
  "islamic-studies": { direction: "left", offset: [-10, 0] },
  mclennan: { direction: "right", offset: [10, 0] },
};
const COMPACT_LABEL_POSITIONS: Record<string, Pick<L.TooltipOptions, "direction" | "offset">> = {
  burnside: { direction: "left", offset: [-8, 0] },
  "islamic-studies": { direction: "top", offset: [20, -6] },
  law: { direction: "right", offset: [8, 0] },
  mclennan: { direction: "bottom", offset: [0, 8] },
};

export interface XRayOverlay {
  aps: AccessPoint[]; scanners: ScannerReading[]; names: string[]; stats: ScannerStats[]; constants: XRayConstants;
}
const fmt = (n: number) => (Math.round(n * 10) / 10).toString();

export default function BuildingMap({ buildings, selectedId, focusId = null, xray = null, onSelect }: {
  buildings: MapBuilding[]; selectedId: string | null; focusId?: string | null; xray?: XRayOverlay | null;
  onSelect: (id: string | null) => void;
}) {
  const xrayLayer = useRef<L.LayerGroup | null>(null);
  const [selectedScanners, setSelectedScanners] = useState<Set<string>>(() => new Set());
  const el = useRef<HTMLDivElement>(null);
  const map = useRef<L.Map | null>(null);
  const shapes = useRef(new Map<string, L.Polygon>());
  const onSelectRef = useRef(onSelect);
  onSelectRef.current = onSelect;

  useEffect(() => {
    if (!el.current) return;
    // A generous renderer padding keeps footprints drawn beyond the viewport, so they never lag behind a pan.
    const m = L.map(el.current, { zoomControl: false, attributionControl: true, zoomSnap: 0.25, renderer: L.svg({ padding: 1.5 }) });
    m.fitBounds(L.latLngBounds(Object.values(BUILDING_SHAPES).flat()), { padding: [36, 36] });
    m.attributionControl.setPrefix(false);
    L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 21, maxNativeZoom: 19, attribution: "© OpenStreetMap",
    }).addTo(m);
    m.on("click", () => onSelectRef.current(null));
    xrayLayer.current = L.layerGroup().addTo(m);
    map.current = m;
    return () => { m.remove(); map.current = null; shapes.current.clear(); };
  }, []);

  useEffect(() => {
    const m = map.current;
    if (!m) return;
    for (const [id, coords] of Object.entries(BUILDING_SHAPES)) {
      const b = buildings.find((building) => building.id === id);
      const text = b
        ? `${shortName(b.name)} · ${b.pct === null ? "no data" : b.pct + "% full\n" + levelLabel(b.level)}\n${b.trackedPct}% tracked`
        : `${shortName(id)} · no data\n0% tracked`;
      let p = shapes.current.get(id);
      if (!p) {
        p = L.polygon(coords, { className: "bldg" }).addTo(m);
        const position = m.getSize().x < 600 ? COMPACT_LABEL_POSITIONS[id] : LABEL_POSITIONS[id];
        p.bindTooltip(text, { permanent: true, direction: "top", className: "map-label", offset: [0, -6], ...position });
        p.on("click", (e) => { L.DomEvent.stopPropagation(e); onSelectRef.current(id); });
        shapes.current.set(id, p);
      } else p.setTooltipContent(text);
    }
  }, [buildings]);

  useEffect(() => {
    shapes.current.forEach((p, id) => p.getElement()?.classList.toggle("selected", id === selectedId));
  }, [selectedId, buildings]);

  useEffect(() => {
    const m = map.current;
    const p = selectedId ? shapes.current.get(selectedId) : null;
    if (!m || !p) return;
    // On desktop the sidebar resize changes Leaflet's size. Starting flyTo during that resize makes
    // the SVG renderer and tile pane use different origins, visibly lifting footprints off buildings.
    const delay = window.matchMedia("(min-width: 768px)").matches ? 520 : 0;
    const t = setTimeout(() => {
      m.invalidateSize();
      m.flyTo(p.getBounds().getCenter(), Math.max(m.getZoom(), 17), { duration: 0.8 });
    }, delay);
    return () => clearTimeout(t);
  }, [selectedId]);

  useEffect(() => {
    const m = map.current;
    const p = focusId ? shapes.current.get(focusId) : null;
    if (!m || !p) return;
    // Wait for the sidebar resize to settle so the building is centred in the visible map.
    const t = setTimeout(() => { m.invalidateSize(); m.flyToBounds(p.getBounds(), { padding: [48, 48], maxZoom: 19, duration: 0.8 }); }, 520);
    return () => clearTimeout(t);
  }, [focusId]);

  useEffect(() => {
    const g = xrayLayer.current;
    if (!g) return;
    g.clearLayers();
    el.current?.classList.toggle("xray-on", !!xray);
    if (!xray) {
      setSelectedScanners((current) => current.size ? new Set() : current);
      return;
    }
    const { closeD, farD, farDownscaleFactor: alpha } = xray.constants;
    for (const ap of xray.aps) {
      L.circleMarker([ap.lat, ap.lon], { radius: 4, className: "xray-ap", interactive: true })
        .bindTooltip(`access point · floor ${ap.floor}`, { className: "map-label" }).addTo(g);
    }
    xray.scanners.forEach((s, i) => {
      if (s.lat == null || s.lon == null) return;
      const x = xray.stats[i];
      const name = xray.names[i];
      if (!x || !name) return;
      const c: L.LatLngExpression = [s.lat, s.lon];
      const selected = selectedScanners.has(name);
      const cls = x.live ? "" : " stale";
      L.circle(c, { radius: farD, className: `xray-far${cls}`, interactive: false }).addTo(g);
      L.circle(c, { radius: closeD, className: `xray-close${cls}`, interactive: false }).addTo(g);
      const marker = L.circleMarker(c, {
        radius: selected ? 6 : 5, className: `xray-scanner${selected ? " selected" : ""}${cls}`, interactive: true,
      }).addTo(g);
      marker.on("click", (e) => {
        L.DomEvent.stopPropagation(e);
        const multi = e.originalEvent.ctrlKey || e.originalEvent.metaKey;
        setSelectedScanners((current) => {
          const onlyThis = current.size === 1 && current.has(name);
          const next = new Set(multi || onlyThis ? current : []);
          if (next.has(name)) next.delete(name); else next.add(name);
          return next;
        });
      });
      if (!selected) return;
      let body = `scanner ${name} · floor ${s.floor ?? "?"}\nclose ${x.c}`;
      if (!x.live) body += "\nstale, not counted";
      else if (x.kept != null) {
        body += `\nkept ${fmt(x.kept)} / ${fmt(x.w)}`;
        if (x.w - x.kept > 0.005) body += `\n−${fmt(x.w - x.kept)} overlap`;
      }
      L.marker(c, { interactive: false, icon: L.divIcon({ className: "xray-label", iconSize: undefined, html: `<div>${body}</div>` }) }).addTo(g);
      const edge: L.LatLngExpression = [s.lat + (farD / 6371000) * (180 / Math.PI), s.lon];
      L.marker(edge, { interactive: false, icon: L.divIcon({ className: "xray-label far", iconSize: undefined,
        html: `<div>far ${fmt(alpha * x.b)} (${x.b} × ${alpha})</div>` }) }).addTo(g);
    });
  }, [xray, selectedScanners]);

  useEffect(() => {
    const ro = new ResizeObserver(() => map.current?.invalidateSize());
    if (el.current) ro.observe(el.current);
    return () => ro.disconnect();
  }, []);

  return <div ref={el} className="h-full w-full" />;
}
