import { motion } from "framer-motion";
import { effectiveLevel, isNoData, levelLabel, pctLabel, shortName, type FloorEntry } from "@/lib/larp";
import { levelBg } from "./levels";

export function FloorRow({ f, onOpen }: { f: FloorEntry; onOpen: () => void }) {
  const lvl = effectiveLevel(f);
  return (
    <motion.button layout="position" onClick={onOpen}
      initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
      transition={{ type: "spring", stiffness: 380, damping: 34 }}
      className={`${levelBg[lvl]} group flex w-full items-center justify-between border-b border-background/40 px-6 py-6 text-left text-level-foreground transition-[filter] hover:brightness-95 ${isNoData(f) ? "text-muted-foreground" : ""}`}>
      <span className="text-sm sm:text-base">{shortName(f.buildingName)} floor {f.floor}</span>
      <span className="text-sm sm:text-base">
        {isNoData(f) ? <span className="italic">no data</span> : <>{pctLabel(f)} <em className="ml-1">{levelLabel(lvl)}</em></>}
        <span className="ml-3 inline-block opacity-0 transition-all group-hover:translate-x-1 group-hover:opacity-60">→</span>
      </span>
    </motion.button>
  );
}
