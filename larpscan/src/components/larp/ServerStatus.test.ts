import { describe, expect, it } from "vitest";
import { lastSeenLabel } from "./ServerStatus";

describe("server last-seen labels", () => {
  const now = Date.parse("2026-11-22T12:00:00Z");
  it("uses time today, yesterday, weekday within a week, then ages ago", () => {
    expect(lastSeenLabel(Date.parse("2026-11-22T05:05:00Z"), now)).toMatch(/^last seen \d{2}:\d{2}$/);
    expect(lastSeenLabel(Date.parse("2026-11-21T23:00:00Z"), now)).toBe("last seen yesterday");
    expect(lastSeenLabel(Date.parse("2026-11-20T12:00:00Z"), now)).toBe("last seen friday");
    expect(lastSeenLabel(Date.parse("2026-11-01T12:00:00Z"), now)).toBe("last seen ages ago...");
    expect(lastSeenLabel(null, now)).toBe("last seen ages ago...");
  });
});
