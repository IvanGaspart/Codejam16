import { describe, expect, it } from "vitest";
import {
  buildingBusyness, calculateTrend, levelFromPct, mergeLibraryBuildings, mockFloorHistory, mockFloors,
  pctLabel, placeholderFloors, sortForList, type FloorBusyness,
} from "./larp";
const f = (floor: number, pct: number, covered = true): FloorBusyness => ({
  floor, capacity: 100, covered, scanners: 1, estimatedPeople: pct, busynessPct: pct, level: covered ? levelFromPct(pct) : "NO_DATA",
});
describe("busyness rules", () => {
  it("uses the level thresholds", () => {
    expect([29, 30, 59, 60, 84, 85].map(levelFromPct)).toEqual(["QUIET", "MODERATE", "MODERATE", "BUSY", "BUSY", "FULL"]);
  });
  it("never shows 0% for uncovered floors", () => {
    expect(pctLabel(f(1, 0, false))).toBe("no data");
  });
  it("sorts every floor in descending order by default", () => {
    expect(sortForList([f(2, 20), f(4, 0, false), f(1, 50), f(3, 50)]).map((x) => x.floor)).toEqual([4, 3, 2, 1]);
  });
  it("optionally sorts least busy first, ties to lower floor, no data last", () => {
    expect(sortForList([f(4, 0, false), f(3, 50), f(2, 20), f(1, 50)], "busyness").map((x) => x.floor)).toEqual([2, 1, 3, 4]);
  });
  it("averages covered floors only and reports tracked coverage", () => {
    expect(buildingBusyness([f(1, 20), f(2, 40), f(3, 0, false)])).toEqual({ pct: 30, level: "MODERATE", trackedPct: 67 });
  });
  it("compares the latest reading with ten minutes earlier", () => {
    expect(calculateTrend([
      { at: "2026-01-01T12:00:00Z", busynessPct: 40, covered: true },
      { at: "2026-01-01T12:09:00Z", busynessPct: 46, covered: true },
      { at: "2026-01-01T12:15:00Z", busynessPct: 35, covered: true },
    ])).toEqual({ current: 35, previous: 40, change: -5 });
  });
  it("does not invent a trend without enough history", () => {
    expect(calculateTrend([{ at: "2026-01-01T12:05:00Z", busynessPct: 35, covered: true }])).toBeNull();
  });
});

describe("library catalogue", () => {
  it.each([
    ["schulich", [0, 1, 2, 3, 4, 5, 6], 200],
    ["mclennan", [1, 2, 3, 4, 5, 6], 200],
    ["redpath", [0, 1, 2, 3], 150],
    ["law", [0, 1, 2, 3, 4, 5], 50],
    ["islamic-studies", [1, 2], 40],
    ["burnside", [0, 4], 70],
  ])("gives %s its requested floors and capacity", (id, floors, capacity) => {
    const entries = placeholderFloors(id);
    expect(entries.map((f) => f.floor)).toEqual(floors);
    expect(entries.every((f) => f.capacity === capacity)).toBe(true);
    expect(entries.every((f) => !f.covered && f.level === "NO_DATA")).toBe(true);
  });

  it("keeps all seven mapped buildings present alongside the live API", () => {
    expect(mergeLibraryBuildings([{ id: "trottier", name: "Trottier Building" }]).map((b) => b.id)).toEqual([
      "trottier", "schulich", "mclennan", "redpath", "law", "islamic-studies", "burnside",
    ]);
  });
});

describe("dynamic preview data", () => {
  const start = Date.parse("2026-11-22T12:00:00Z");
  it("changes busyness and scanner amounts over time", () => {
    const before = mockFloors("schulich", start);
    const after = mockFloors("schulich", start + 60000);
    expect(after.map((f) => f.busynessPct)).not.toEqual(before.map((f) => f.busynessPct));
    expect(after.map((f) => f.scanners)).not.toEqual(before.map((f) => f.scanners));
  });
  it("provides enough fake history for a ten-minute trend", () => {
    const history = mockFloorHistory("burnside", 4, start);
    expect(history).toHaveLength(16);
    expect(calculateTrend(history)).not.toBeNull();
  });
});
