export type BusynessLevel = "QUIET" | "MODERATE" | "BUSY" | "FULL" | "NO_DATA";
export interface Building { id: string; name: string }
export interface FloorBusyness {
  floor: number; capacity: number; covered: boolean; scanners: number;
  estimatedPeople: number; busynessPct: number; level: BusynessLevel;
}
export interface FloorEntry extends FloorBusyness { buildingId: string; buildingName: string }
export interface HistoryPoint { at: string; busynessPct: number; covered: boolean }
export interface FloorTrend { change: number; current: number; previous: number }
export interface LiveSnapshot {
  savedAt: number;
  buildings: Building[];
  floors: Record<string, FloorBusyness[]>;
}
export interface LibraryDefinition extends Building {
  floors: { floor: number; capacity: number }[];
}

export const API_BASE = (
  (import.meta.env["VITE_API_BASE_URL"] as string | undefined) || "https://codejam16-ui0a.onrender.com"
).replace(/\/+$/, "");

async function getJson<T>(path: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`);
  if (!res.ok) throw new Error(`request failed (${res.status})`);
  return (await res.json()) as T;
}
export const fetchBuildings = () => getJson<Building[]>("/api/buildings");
export const fetchFloors = (id: string) =>
  getJson<FloorBusyness[]>(`/api/buildings/${encodeURIComponent(id)}/floors`);
export const fetchFloorHistory = (buildingId: string, floor: number) =>
  getJson<HistoryPoint[]>(`/api/buildings/${encodeURIComponent(buildingId)}/floors/${floor}/history`);

const SNAPSHOT_KEY = "larp-last-live-snapshot";
export function loadSnapshot(): LiveSnapshot | null {
  try {
    const raw = localStorage.getItem(SNAPSHOT_KEY);
    if (!raw) return null;
    const value = JSON.parse(raw) as LiveSnapshot;
    return Array.isArray(value.buildings) && value.floors && typeof value.savedAt === "number" ? value : null;
  } catch { return null; }
}
export function saveSnapshot(snapshot: LiveSnapshot) {
  try { localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(snapshot)); } catch { /* storage may be unavailable */ }
}

/** Compare the latest covered reading with the newest reading at least ten minutes old. */
export function calculateTrend(points: HistoryPoint[], minutes = 10): FloorTrend | null {
  const covered = points.filter((p) => p.covered && Number.isFinite(Date.parse(p.at)))
    .sort((a, b) => Date.parse(a.at) - Date.parse(b.at));
  const latest = covered.at(-1);
  if (!latest) return null;
  const cutoff = Date.parse(latest.at) - minutes * 60000;
  const previous = [...covered].reverse().find((p) => Date.parse(p.at) <= cutoff);
  if (!previous) return null;
  return { current: latest.busynessPct, previous: previous.busynessPct, change: latest.busynessPct - previous.busynessPct };
}

const floorRange = (from: number, to: number, capacity: number) =>
  Array.from({ length: to - from + 1 }, (_, i) => ({ floor: from + i, capacity }));

export const LIBRARY_DEFINITIONS: readonly LibraryDefinition[] = [
  { id: "trottier", name: "Trottier Building", floors: [
    { floor: 1, capacity: 120 }, { floor: 2, capacity: 100 }, { floor: 3, capacity: 100 },
    { floor: 4, capacity: 80 }, { floor: 5, capacity: 90 },
  ] },
  { id: "schulich", name: "Schulich", floors: floorRange(0, 6, 200) },
  { id: "mclennan", name: "McLennan", floors: floorRange(1, 6, 200) },
  { id: "redpath", name: "Redpath", floors: floorRange(0, 3, 150) },
  { id: "law", name: "Law", floors: floorRange(0, 5, 50) },
  { id: "islamic-studies", name: "Islamic Studies", floors: floorRange(1, 2, 40) },
  { id: "burnside", name: "Burnside", floors: [{ floor: 0, capacity: 70 }, { floor: 4, capacity: 70 }] },
];

export const MOCK_BUILDINGS: Building[] = LIBRARY_DEFINITIONS.map(({ id, name }) => ({ id, name }));

/** Keep every mapped library visible even when the live API does not know it yet. */
export function mergeLibraryBuildings(remote: Building[] = []): Building[] {
  const known = LIBRARY_DEFINITIONS.map(({ id, name }) => remote.find((b) => b.id === id) ?? { id, name });
  const knownIds = new Set(LIBRARY_DEFINITIONS.map((b) => b.id));
  return [...known, ...remote.filter((b) => !knownIds.has(b.id))];
}

export function placeholderFloors(buildingId: string): FloorBusyness[] {
  const definition = LIBRARY_DEFINITIONS.find((b) => b.id === buildingId);
  return (definition?.floors ?? []).map(({ floor, capacity }) => ({
    floor, capacity, covered: false, scanners: 0, estimatedPeople: 0, busynessPct: 0, level: "NO_DATA",
  }));
}

function hash(value: string): number {
  let result = 2166136261;
  for (let i = 0; i < value.length; i++) result = Math.imul(result ^ value.charCodeAt(i), 16777619);
  return result >>> 0;
}

function mockPct(buildingId: string, floor: number, at: number): number {
  const seed = hash(`${buildingId}:${floor}`);
  const tick = Math.floor(at / 15000);
  const phase = (seed % 628) / 100;
  const base = 24 + (seed % 34);
  const wave = Math.sin(tick * 0.31 + phase) * 25 + Math.cos(tick * 0.13 + phase * 0.7) * 12;
  return Math.max(4, Math.min(96, Math.round(base + wave)));
}

/** Deterministic but time-varying preview data, shared by floors and their histories. */
export function mockFloors(buildingId: string, at = Date.now()): FloorBusyness[] {
  const definition = LIBRARY_DEFINITIONS.find((b) => b.id === buildingId);
  return (definition?.floors ?? []).map(({ floor, capacity }) => {
    const busynessPct = mockPct(buildingId, floor, at);
    return {
      floor, capacity, covered: true,
      scanners: 1 + ((hash(`${buildingId}:scanners`) + Math.floor(at / 30000) + floor) % 4),
      estimatedPeople: Math.round(capacity * busynessPct / 100),
      busynessPct, level: levelFromPct(busynessPct),
    };
  });
}

export function mockFloorHistory(buildingId: string, floor: number, at = Date.now()): HistoryPoint[] {
  const latest = Math.floor(at / 60000) * 60000;
  return Array.from({ length: 21 }, (_, i) => {
    const timestamp = latest - (20 - i) * 60000;
    return { at: new Date(timestamp).toISOString(), busynessPct: mockPct(buildingId, floor, timestamp), covered: true };
  });
}

export function levelFromPct(pct: number): Exclude<BusynessLevel, "NO_DATA"> {
  if (pct >= 85) return "FULL";
  if (pct >= 60) return "BUSY";
  if (pct >= 30) return "MODERATE";
  return "QUIET";
}
export const isNoData = (f: Pick<FloorBusyness, "covered" | "level">) => !f.covered || f.level === "NO_DATA";
export const effectiveLevel = (f: FloorBusyness): BusynessLevel => (isNoData(f) ? "NO_DATA" : f.level);
export const pctLabel = (f: FloorBusyness) => (isNoData(f) ? "no data" : `${f.busynessPct}%`);
export const levelLabel = (l: BusynessLevel) => (l === "NO_DATA" ? "NO DATA" : l);

export type FloorSort = "floor" | "busyness";

/** Floor order is descending; busyness order is least busy first with no data last. */
export function sortForList<T extends FloorBusyness>(floors: T[], sort: FloorSort = "floor"): T[] {
  return [...floors].sort((a, b) => {
    if (sort === "floor") return b.floor - a.floor;
    const an = isNoData(a), bn = isNoData(b);
    if (an !== bn) return an ? 1 : -1;
    if (!an && a.busynessPct !== b.busynessPct) return a.busynessPct - b.busynessPct;
    return a.floor - b.floor;
  });
}

/** Building busyness = average of covered floors; NO_DATA when none covered. */
export function buildingBusyness(floors: FloorBusyness[] | undefined): { pct: number | null; level: BusynessLevel; trackedPct: number } {
  const all = floors ?? [];
  const live = all.filter((f) => !isNoData(f));
  const trackedPct = all.length ? Math.round((live.length / all.length) * 100) : 0;
  if (!live.length) return { pct: null, level: "NO_DATA", trackedPct };
  const pct = Math.round(live.reduce((s, f) => s + f.busynessPct, 0) / live.length);
  return { pct, level: levelFromPct(pct), trackedPct };
}

export function matchesSearch(f: FloorEntry, query: string, maxPct: number): boolean {
  if (!isNoData(f) && f.busynessPct > maxPct) return false;
  if (isNoData(f) && maxPct < 100) return false;
  const q = query.trim().toLowerCase();
  if (!q) return true;
  const lvl = levelLabel(effectiveLevel(f)).toLowerCase();
  return f.buildingName.toLowerCase().includes(q) || lvl.includes(q) || lvl.replace(" ", "_").includes(q);
}

export const shortName = (name: string) => name.replace(/\s*building\s*/i, " ").trim().toLowerCase();

/** Exact OpenStreetMap building footprints for the McGill campus. */
export const BUILDING_SHAPES: Record<string, [number, number][]> = {
  trottier: [
    [45.5071566, -73.5788907], [45.5071765, -73.5789311],
    [45.5071630, -73.5789427], [45.5071864, -73.5789904],
    [45.5073490, -73.5793223], [45.5073614, -73.5793092],
    [45.5073924, -73.5793745], [45.5074381, -73.5793320],
    [45.5074490, -73.5793219], [45.5074672, -73.5793652],
    [45.5076142, -73.5792238], [45.5074233, -73.5788180],
    [45.5074150, -73.5788003], [45.5073626, -73.5786889],
    [45.5072336, -73.5788152], [45.5072184, -73.5788301],
  ],
  schulich: [
    [45.5049408, -73.5755030], [45.5049376, -73.5755308], [45.5049460, -73.5755541],
    [45.5049541, -73.5755693], [45.5049690, -73.5755850], [45.5049884, -73.5755850],
    [45.5050045, -73.5755798], [45.5050093, -73.5755756], [45.5050169, -73.5755689],
    [45.5050279, -73.5755525], [45.5050313, -73.5755399], [45.5050361, -73.5755224],
    [45.5050419, -73.5755170], [45.5050893, -73.5754731], [45.5051533, -73.5754117],
    [45.5052189, -73.5753466], [45.5052120, -73.5753325], [45.5052653, -73.5752797],
    [45.5051528, -73.5750534], [45.5051434, -73.5750629], [45.5050262, -73.5751793],
    [45.5050193, -73.5751654], [45.5050167, -73.5751606], [45.5050127, -73.5751562],
    [45.5050096, -73.5751536], [45.5050059, -73.5751521], [45.5050006, -73.5751521],
    [45.5049949, -73.5751526], [45.5049908, -73.5751543], [45.5049867, -73.5751583],
    [45.5049841, -73.5751617], [45.5049813, -73.5751665], [45.5049789, -73.5751728],
    [45.5049779, -73.5751812], [45.5049777, -73.5751887], [45.5049791, -73.5751955],
    [45.5049819, -73.5752029], [45.5049901, -73.5752180], [45.5048622, -73.5753464],
  ],
  mclennan: [
    [45.5032206, -73.5764157], [45.5031913, -73.5763552], [45.5031376, -73.5764052],
    [45.5031223, -73.5763750], [45.5031363, -73.5763602], [45.5030847, -73.5762487],
    [45.5030690, -73.5762635], [45.5029833, -73.5760827], [45.5029934, -73.5760712],
    [45.5029408, -73.5759603], [45.5029296, -73.5759705], [45.5029122, -73.5759351],
    [45.5029619, -73.5758832], [45.5029328, -73.5758187], [45.5029594, -73.5757947],
    [45.5029678, -73.5758122], [45.5030631, -73.5757203], [45.5030565, -73.5757021],
    [45.5030809, -73.5756786], [45.5030875, -73.5756933], [45.5031846, -73.5755984],
    [45.5031775, -73.5755826], [45.5032011, -73.5755604], [45.5032109, -73.5755797],
    [45.5033106, -73.5754821], [45.5033037, -73.5754642], [45.5033258, -73.5754417],
    [45.5033555, -73.5755026], [45.5034042, -73.5754557], [45.5034215, -73.5754913],
    [45.5034138, -73.5754985], [45.5034679, -73.5756100], [45.5034846, -73.5756444],
    [45.5035365, -73.5757537], [45.5035544, -73.5757899], [45.5036069, -73.5758982],
    [45.5036169, -73.5758897], [45.5036321, -73.5759228], [45.5036187, -73.5759366],
    [45.5035829, -73.5759737], [45.5036113, -73.5760362], [45.5035868, -73.5760611],
    [45.5035794, -73.5760467], [45.5035369, -73.5760878], [45.5035013, -73.5761235],
    [45.5034840, -73.5761405], [45.5034907, -73.5761545], [45.5034644, -73.5761777],
    [45.5034589, -73.5761648], [45.5034128, -73.5762105], [45.5033712, -73.5762483],
    [45.5033610, -73.5762583], [45.5033673, -73.5762728], [45.5033456, -73.5762956],
    [45.5033370, -73.5762816], [45.5032864, -73.5763310], [45.5032476, -73.5763690],
    [45.5032391, -73.5763783], [45.5032451, -73.5763912],
  ],
  redpath: [
    [45.5036240, -73.5772941], [45.5035969, -73.5772359], [45.5034376, -73.5768936],
    [45.5032879, -73.5765721], [45.5032520, -73.5764987], [45.5032502, -73.5764950],
    [45.5032899, -73.5764547], [45.5033277, -73.5764177], [45.5034095, -73.5763325],
    [45.5034311, -73.5763112], [45.5034499, -73.5762931], [45.5035214, -73.5762243],
    [45.5037440, -73.5766859], [45.5036978, -73.5767318], [45.5037875, -73.5769174],
    [45.5038540, -73.5770548], [45.5038609, -73.5770691],
  ],
  law: [
    [45.5037144, -73.5807234], [45.5036537, -73.5805991], [45.5036861, -73.5805669],
    [45.5036325, -73.5805432], [45.5036294, -73.5805493], [45.5035953, -73.5804799],
    [45.5034970, -73.5805793], [45.5033506, -73.5807242], [45.5033259, -73.5806739],
    [45.5032349, -73.5807648], [45.5031988, -73.5806912], [45.5031609, -73.5807291],
    [45.5031370, -73.5806804], [45.5030660, -73.5807512], [45.5031011, -73.5808231],
    [45.5030838, -73.5808404], [45.5031171, -73.5809087], [45.5031525, -73.5809810],
    [45.5033348, -73.5807996], [45.5034454, -73.5810259], [45.5035625, -73.5809095],
    [45.5035341, -73.5808514], [45.5036429, -73.5807432], [45.5036598, -73.5807777],
  ],
  "islamic-studies": [
    [45.5038924, -73.5779257], [45.5038139, -73.5777735], [45.5038357, -73.5777523],
    [45.5038456, -73.5777432], [45.5040620, -73.5775433], [45.5040256, -73.5775375],
    [45.5039948, -73.5774792], [45.5039993, -73.5774147], [45.5040402, -73.5773697],
    [45.5040845, -73.5773843], [45.5041134, -73.5774436], [45.5041065, -73.5774985],
    [45.5041260, -73.5775393], [45.5041922, -73.5776716], [45.5041137, -73.5777507],
    [45.5040978, -73.5777187], [45.5039169, -73.5779010],
  ],
  burnside: [
    [45.5046531, -73.5752097], [45.5045141, -73.5749266], [45.5045434, -73.5748973],
    [45.5045358, -73.5748818], [45.5045955, -73.5748219], [45.5046125, -73.5748049],
    [45.5047404, -73.5746769], [45.5047487, -73.5746938], [45.5047758, -73.5746667],
    [45.5049171, -73.5749542], [45.5048857, -73.5749857], [45.5048929, -73.5750007],
    [45.5047677, -73.5751275], [45.5047522, -73.5751432], [45.5046924, -73.5752031],
    [45.5046822, -73.5751823],
  ],
};
export const MAP_CENTER: [number, number] = [45.5051, -73.5775];

export interface WaitEstimate { wait: string; hint: string; minutes: number | null }
const FULL_AT = 85;

/** Busyness change per minute over [end - minutes, end], or null without enough history. */
function rate(points: HistoryPoint[], endMs: number, minutes: number): number | null {
  const covered = points.filter((p) => p.covered && Date.parse(p.at) <= endMs).sort((a, b) => Date.parse(a.at) - Date.parse(b.at));
  const last = covered.at(-1);
  if (!last) return null;
  const lastMs = Date.parse(last.at);
  const prev = [...covered].reverse().find((p) => Date.parse(p.at) <= lastMs - minutes * 60000);
  if (!prev) return null;
  return (last.busynessPct - prev.busynessPct) / ((lastMs - Date.parse(prev.at)) / 60000);
}

/**
 * Full floors: project busyness with p(t) = p0 + v·t + ½·a·t², where v is the last-10-minute rate and
 * a is how that rate changed versus the 10 minutes before. Returns minutes until below FULL.
 */
export function estimateWait(f: FloorBusyness, history: HistoryPoint[]): WaitEstimate {
  const lvl = effectiveLevel(f);
  if (lvl === "NO_DATA") return { wait: "unknown", hint: "no sensors here yet", minutes: null };
  if (lvl === "QUIET" || lvl === "MODERATE") return { wait: "none", hint: "head over!", minutes: null };
  if (lvl === "BUSY") return { wait: "short", hint: "maybe grab a coffee", minutes: null };
  const latest = history.filter((p) => p.covered).map((p) => Date.parse(p.at)).filter(Number.isFinite).sort((a, b) => a - b).at(-1);
  const v = latest === undefined ? null : rate(history, latest, 10);
  if (v === null || latest === undefined) return { wait: "likely", hint: "try another floor", minutes: null };
  const vPrev = rate(history, latest - 10 * 60000, 10);
  const a = vPrev === null ? 0 : (v - vPrev) / 10;
  for (let t = 1; t <= 60; t++) {
    if (f.busynessPct + v * t + 0.5 * a * t * t < FULL_AT) {
      const minutes = t;
      return { wait: `~${minutes} min`, hint: "it's emptying out", minutes };
    }
  }
  return { wait: "likely", hint: "try another floor", minutes: null };
}
