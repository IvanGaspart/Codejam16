import { API_BASE } from "./larp";

/** One Scanner's latest batch, as the backend reports it (ids are never displayed). */
export interface ScannerReading {
  id?: string; label?: string; floor: number | null; lat: number | null; lon: number | null;
  ageS: number; rssis: number[];
}
export interface AccessPoint { lat: number; lon: number; floor: number }
export interface XRayConstants {
  windowSeconds: number; k: number; closeRssi: number; closeD: number; farRssi: number; farD: number; farDownscaleFactor: number;
}
/** Used only until the server publishes its own constants. */
export const DEFAULT_CONSTANTS: XRayConstants = {
  windowSeconds: 120, k: 1.4, closeRssi: -67, closeD: 6, farRssi: -85, farD: 18, farDownscaleFactor: 0.6,
};

/** Trottier access-point positions (ap_gps_trottier.csv). */
export const ACCESS_POINTS: Record<string, AccessPoint[]> = {
  trottier: [
  { lat: 45.50748809686601, lon: -73.5791398381657, floor: 5 },
  { lat: 45.507382832930176, lon: -73.57920886706448, floor: 5 },
  { lat: 45.507213014400676, lon: -73.5789142543538, floor: 5 },
  { lat: 45.50725059306509, lon: -73.57897196767652, floor: 5 },
  { lat: 45.50741460553359, lon: -73.57931800514316, floor: 5 },
  { lat: 45.50750671259689, lon: -73.57925564378615, floor: 5 },
  { lat: 45.507442316886454, lon: -73.57903570508513, floor: 5 },
  { lat: 45.507361502922706, lon: -73.57880570322949, floor: 5 },
  { lat: 45.507361502922706, lon: -73.5789223793168, floor: 3 },
  { lat: 45.50722663146655, lon: -73.57890561551115, floor: 3 },
  { lat: 45.5073446462334, lon: -73.57912498757777, floor: 3 },
  { lat: 45.50731808093191, lon: -73.57916199118493, floor: 3 },
  { lat: 45.50748632762956, lon: -73.57911866988874, floor: 3 },
  { lat: 45.5074148544698, lon: -73.57901487928326, floor: 3 },
  { lat: 45.50741169193851, lon: -73.57932264099166, floor: 3 },
  { lat: 45.507468617474515, lon: -73.5792856373845, floor: 3 },
  { lat: 45.5073597134158, lon: -73.57880951596312, floor: 4 },
  { lat: 45.50727508261756, lon: -73.57899925547017, floor: 4 },
  { lat: 45.50721399096345, lon: -73.57889867263424, floor: 4 },
  { lat: 45.50730609835504, lon: -73.57888794379842, floor: 4 },
  { lat: 45.50735777648792, lon: -73.57920159448446, floor: 4 },
  { lat: 45.507531652116334, lon: -73.57910235274241, floor: 4 },
  { lat: 45.50757087485228, lon: -73.57921992149952, floor: 4 },
  { lat: 45.50755395725899, lon: -73.57927356567868, floor: 4 },
  { lat: 45.50742049606699, lon: -73.5793352564847, floor: 4 },
  ],
};

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`);
  if (!res.ok) throw new Error(`request failed (${res.status})`);
  return (await res.json()) as T;
}
export const fetchScanners = (buildingId: string) =>
  get<ScannerReading[]>(`/api/buildings/${encodeURIComponent(buildingId)}/scanners`);
export const fetchConstants = () =>
  get<Partial<XRayConstants>>("/api/xray/constants").then((c) => ({ ...DEFAULT_CONSTANTS, ...c }));

const EARTH_RADIUS_M = 6371000;
const GRID_STEP_M = 0.25;

export interface ScannerStats { c: number; b: number; w: number; kept: number | null; live: boolean }
export interface FloorDedup {
  floor: number; scanners: number; raw: number; overlap: number; positionless: number; devices: number; people: number;
}

export function strata(s: ScannerReading, p: XRayConstants) {
  let c = 0, b = 0;
  for (const r of s.rssis) { if (r >= p.closeRssi) c++; else if (r >= p.farRssi) b++; }
  return { c, b, w: c + p.farDownscaleFactor * b };
}

function project(lat: number, lon: number, lat0: number, lon0: number): [number, number] {
  const rad = Math.PI / 180;
  return [(lon - lon0) * rad * Math.cos(lat0 * rad) * EARTH_RADIUS_M, (lat - lat0) * rad * EARTH_RADIUS_M];
}

type Positioned = ScannerReading & { lat: number; lon: number };

/** Each positioned Scanner keeps w × Σ(ρ/k)/Σρ, where k counts the far discs covering a grid cell. */
function union(pos: Positioned[], p: XRayConstants, st: ScannerStats[], idx: number[]): number {
  if (!pos.length) return 0;
  const { closeD, farD, farDownscaleFactor: alpha } = p;
  const xy = pos.map((s) => project(s.lat, s.lon, pos[0]!.lat, pos[0]!.lon));
  const cells = Math.ceil((2 * farD) / GRID_STEP_M);
  let total = 0;
  pos.forEach((_, i) => {
    const x = st[idx[i]!]!;
    const closeDensity = x.c / (Math.PI * closeD * closeD);
    const farDensity = (alpha * x.b) / (Math.PI * (farD * farD - closeD * closeD));
    const [ox, oy] = xy[i]!;
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
        for (const [tx, ty] of xy) { const ex = ox + dx - tx, ey = oy + dy - ty; if (ex * ex + ey * ey <= farD * farD) k++; }
        num += rho / k; den += rho;
      }
    }
    x.kept = den > 0 ? (x.w * num) / den : 0;
    total += x.kept;
  });
  return total;
}

/** Mirrors the backend's overlap-aware estimate so X-Ray can show its working. */
export function computeDedup(scanners: ScannerReading[], p: XRayConstants) {
  const stats: ScannerStats[] = scanners.map((s) => ({ ...strata(s, p), kept: null, live: s.floor != null && s.ageS <= p.windowSeconds }));
  const floors = [...new Set(scanners.map((s) => s.floor).filter((f): f is number => f != null))].sort((a, b) => b - a);
  const result: FloorDedup[] = floors.map((floor) => {
    const idx = scanners.map((_, i) => i).filter((i) => stats[i]!.live && scanners[i]!.floor === floor);
    const posIdx = idx.filter((i) => scanners[i]!.lat != null && scanners[i]!.lon != null);
    let positionless = 0;
    for (const i of idx) if (!posIdx.includes(i)) positionless = Math.max(positionless, stats[i]!.w);
    const raw = posIdx.reduce((a, i) => a + stats[i]!.w, 0);
    const u = union(posIdx.map((i) => scanners[i] as Positioned), p, stats, posIdx);
    const devices = Math.max(u, positionless);
    return { floor, scanners: idx.length, raw, overlap: raw - u, positionless, devices, people: devices / p.k };
  });
  return { stats, floors: result };
}

/** Friendly, id-free names: scanner A, B, C… in a stable order. */
export function scannerNames(scanners: ScannerReading[]): string[] {
  const keys = scanners.map((s, i) => s.id ?? s.label ?? String(i));
  const sorted = [...keys].sort();
  return keys.map((k) => { const n = sorted.indexOf(k); return n < 26 ? String.fromCharCode(65 + n) : `#${n + 1}`; });
}

/** Moving, overlapping preview scanners for Option-P mode. */
export function mockScanners(buildingId: string, at = Date.now()): ScannerReading[] {
  const aps = ACCESS_POINTS[buildingId] ?? [];
  if (!aps.length) return [];
  const t = at / 20000;
  const spots = [[0, 5], [6, 5], [11, 3], [15, 3], [18, 4], [23, 4]] as const;
  return spots.map(([ap, floor], n) => {
    const base = aps[ap]!;
    const count = 8 + Math.round(6 * Math.sin(t + n));
    return {
      id: `mock-${n}`, floor,
      lat: base.lat + Math.sin(t * 0.7 + n) * 0.00003, lon: base.lon + Math.cos(t * 0.5 + n) * 0.00004,
      ageS: 5 + ((n * 7 + Math.floor(t)) % 20),
      rssis: Array.from({ length: count }, (_, i) => -50 - ((i * 13 + n * 5 + Math.floor(t)) % 45)),
    };
  });
}
