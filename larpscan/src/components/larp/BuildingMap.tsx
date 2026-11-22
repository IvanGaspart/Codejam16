import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { useEffect, useRef } from "react";
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

export default function BuildingMap({ buildings, selectedId, onSelect }: {
  buildings: MapBuilding[]; selectedId: string | null; onSelect: (id: string | null) => void;
}) {
  const el = useRef<HTMLDivElement>(null);
  const map = useRef<L.Map | null>(null);
  const shapes = useRef(new Map<string, L.Polygon>());
  const onSelectRef = useRef(onSelect);
  onSelectRef.current = onSelect;

  useEffect(() => {
    if (!el.current) return;
    const m = L.map(el.current, { zoomControl: false, attributionControl: true, zoomSnap: 0.25 });
    m.fitBounds(L.latLngBounds(Object.values(BUILDING_SHAPES).flat()), { padding: [36, 36] });
    m.attributionControl.setPrefix(false);
    L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 20, attribution: "© OpenStreetMap",
    }).addTo(m);
    m.on("click", () => onSelectRef.current(null));
    map.current = m;
    return () => { m.remove(); map.current = null; shapes.current.clear(); };
  }, []);

  useEffect(() => {
    const m = map.current;
    if (!m) return;
    for (const [id, coords] of Object.entries(BUILDING_SHAPES)) {
      const b = buildings.find((building) => building.id === id);
      const text = b
        ? `${shortName(b.name)} · ${b.pct === null ? "no data" : b.pct + "% " + levelLabel(b.level)}\n${b.trackedPct}% tracked`
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
    const p = selectedId ? shapes.current.get(selectedId) : null;
    if (p && map.current) map.current.flyTo(p.getBounds().getCenter(), Math.max(map.current.getZoom(), 17), { duration: 0.8 });
  }, [selectedId, buildings]);

  useEffect(() => {
    const ro = new ResizeObserver(() => map.current?.invalidateSize());
    if (el.current) ro.observe(el.current);
    return () => ro.disconnect();
  }, []);

  return <div ref={el} className="h-full w-full" />;
}
