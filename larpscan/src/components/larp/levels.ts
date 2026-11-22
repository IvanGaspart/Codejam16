import type { BusynessLevel } from "@/lib/larp";
export const levelBg: Record<BusynessLevel, string> = {
  QUIET: "bg-level-quiet", MODERATE: "bg-level-moderate", BUSY: "bg-level-busy", FULL: "bg-level-full", NO_DATA: "bg-level-nodata",
};
