import { useQueries } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { ArrowLeft, ChevronDown, ChevronUp } from "lucide-react";
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { useIsMobile } from "@/hooks/use-mobile";
import { calculateTrend, effectiveLevel, fetchFloorHistory, isNoData, levelLabel, mockFloorHistory, pctLabel, shortName, sortForList, type BusynessLevel, type FloorEntry, type FloorSort } from "@/lib/larp";
import { levelBg } from "./levels";
import { ThemeToggle } from "./ThemeToggle";

const STRIP = 64;
const WAIT: Record<BusynessLevel, [string, string]> = {
  QUIET: ["none", "head over!"], MODERATE: ["none", "head over!"], BUSY: ["short", "maybe grab a coffee"],
  FULL: ["likely", "try another floor"], NO_DATA: ["unknown", "no sensors here yet"],
};

export function FloorView({ floors, initialFloor, originTop, sortMode, preview, onBack }: {
  floors: FloorEntry[]; initialFloor: number; originTop: number; sortMode: FloorSort; preview: boolean; onBack: () => void;
}) {
  const isMobile = useIsMobile();
  const ordered = sortForList(floors, sortMode);
  const historyQs = useQueries({
    queries: ordered.map((f) => ({
      queryKey: ["floor-history", f.buildingId, f.floor, preview],
      queryFn: () => preview ? mockFloorHistory(f.buildingId, f.floor) : fetchFloorHistory(f.buildingId, f.floor),
      enabled: !isNoData(f),
      refetchInterval: preview ? 15000 : 60000,
      retry: 2,
    })),
  });
  const scroller = useRef<HTMLDivElement>(null);
  const [focus, setFocus] = useState(() => Math.max(0, ordered.findIndex((f) => f.floor === initialFloor)));
  const leaving = useRef(false);
  const pull = useRef(0);

  const itemH = () => (scroller.current?.clientHeight ?? 0) - STRIP * 2;
  useLayoutEffect(() => {
    const s = scroller.current;
    if (s) s.scrollTop = focus * itemH();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const back = useCallback(() => { if (!leaving.current) { leaving.current = true; onBack(); } }, [onBack]);
  const goTo = (i: number) => scroller.current?.scrollTo({ top: i * itemH(), behavior: "smooth" });

  useEffect(() => {
    const s = scroller.current;
    if (!s) return;
    const onScroll = () => { const h = itemH(); if (h > 0) setFocus(Math.round(s.scrollTop / h)); };
    const onWheel = (e: WheelEvent) => {
      if (isMobile && s.scrollTop <= 1 && e.deltaY < 0) { pull.current += -e.deltaY; if (pull.current > 160) back(); }
      else pull.current = 0;
    };
    let startY = 0;
    const onTouchStart = (e: TouchEvent) => { startY = e.touches[0]?.clientY ?? 0; };
    const onTouchMove = (e: TouchEvent) => {
      const dy = (e.touches[0]?.clientY ?? 0) - startY;
      if (isMobile && s.scrollTop <= 1 && dy > 110) back();
    };
    s.addEventListener("scroll", onScroll, { passive: true });
    s.addEventListener("wheel", onWheel, { passive: true });
    s.addEventListener("touchstart", onTouchStart, { passive: true });
    s.addEventListener("touchmove", onTouchMove, { passive: true });
    return () => {
      s.removeEventListener("scroll", onScroll); s.removeEventListener("wheel", onWheel);
      s.removeEventListener("touchstart", onTouchStart); s.removeEventListener("touchmove", onTouchMove);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [back, isMobile]);

  const ease = [0.22, 1, 0.36, 1] as const;
  const hiddenClip = isMobile ? `inset(${originTop * 100}% 0% 0% 0%)` : "inset(0% 100% 0% 0%)";
  const exitClip = isMobile ? "inset(0% 0% 100% 0%)" : "inset(0% 100% 0% 0%)";
  return (
    <motion.div className="fixed inset-0 z-30 bg-background md:inset-y-0 md:left-0 md:right-auto md:w-1/2 md:border-r md:border-border"
      initial={{ clipPath: hiddenClip, opacity: 0 }}
      animate={{ clipPath: "inset(0% 0% 0% 0%)", opacity: 1 }}
      exit={{ clipPath: exitClip, opacity: 0 }}
      transition={{ duration: 0.6, ease }}>
      <div className="absolute left-3 top-3 z-50">
        <button onClick={(e) => { e.stopPropagation(); back(); }} onPointerDown={(e) => e.stopPropagation()} aria-label="back to map"
          className="grid h-10 w-10 place-items-center rounded-full border border-border bg-background/80 text-foreground backdrop-blur transition-transform hover:scale-105 active:scale-95">
          <ArrowLeft className="h-4 w-4" />
        </button>
      </div>
      <div className="absolute right-3 top-3 z-50 md:hidden"><ThemeToggle /></div>

      <div ref={scroller} className="no-scrollbar h-full snap-y snap-mandatory overflow-y-scroll overscroll-contain"
        style={{ paddingTop: STRIP, paddingBottom: STRIP, scrollPaddingTop: STRIP }}>
        {ordered.map((f, i) => {
          const lvl = effectiveLevel(f);
          const nd = isNoData(f);
          const rel = i - focus;
          const [wait, hint] = WAIT[lvl];
          const trend = calculateTrend(historyQs[i]?.data ?? []);
          const trendLabel = trend
            ? `${trend.change < 0 ? "↘" : trend.change > 0 ? "↗" : "→"}${trend.change === 0 ? " steady" : Math.abs(trend.change) + "%"} last 10 minutes`
            : "trend · collecting history";
          const trendTone = !trend || trend.change === 0 ? "opacity-70" : trend.change > 0 ? "text-trend-bad" : "text-trend-good";
          return (
            <section key={f.floor} className={`${levelBg[lvl]} relative snap-start text-level-foreground`}
              style={{ height: `calc(100dvh - ${STRIP * 2}px)` }}>
              <button onClick={() => goTo(i)} tabIndex={rel === -1 ? 0 : -1}
                className={`absolute inset-x-0 bottom-0 z-20 flex items-center justify-between px-16 text-sm transition-opacity duration-300 sm:px-20 ${rel === -1 ? "opacity-100" : "pointer-events-none opacity-0"}`}
                style={{ height: STRIP }}>
                <span>{shortName(f.buildingName)} floor {f.floor}</span><ChevronUp className="h-6 w-6" />
                <span>{nd ? <em>no data</em> : <>{pctLabel(f)} <em>{levelLabel(lvl)}</em></>}</span>
              </button>
              <button onClick={() => goTo(i)} tabIndex={rel === 1 ? 0 : -1}
                className={`absolute inset-x-0 top-0 z-20 flex items-center justify-between px-6 text-sm transition-opacity duration-300 sm:px-20 ${rel === 1 ? "opacity-100" : "pointer-events-none opacity-0"}`}
                style={{ height: STRIP }}>
                <span>{shortName(f.buildingName)} floor {f.floor}</span><ChevronDown className="h-6 w-6" />
                <span>{nd ? <em>no data</em> : <>{pctLabel(f)} <em>{levelLabel(lvl)}</em></>}</span>
              </button>

              {rel === 0 && i > 0 && (
                <button aria-label={`go up to floor ${ordered[i - 1]?.floor ?? ""}`} tabIndex={-1}
                  onClick={() => goTo(i - 1)} className="absolute inset-x-0 top-0 z-0 h-[20%] md:hidden" />
              )}
              {rel === 0 && i < ordered.length - 1 && (
                <button aria-label={`go down to floor ${ordered[i + 1]?.floor ?? ""}`} tabIndex={-1}
                  onClick={() => goTo(i + 1)} className="absolute inset-x-0 bottom-0 z-0 h-[20%] md:hidden" />
              )}

              <div className={`pointer-events-none relative z-10 flex h-full flex-col items-center justify-center px-6 transition-all duration-500 ${rel === 0 ? "scale-100 opacity-100" : "scale-95 opacity-0"}`}>
                <h2 className="text-center text-2xl leading-tight sm:text-3xl">
                  <span className="font-bold">{shortName(f.buildingName)}</span><br />floor {f.floor}
                </h2>
                <div className="mt-16 flex flex-wrap items-baseline justify-center gap-x-4 gap-y-2">
                  {nd ? <span className="text-5xl italic sm:text-6xl">no data</span> : (
                    <><span className="text-6xl font-bold tabular-nums sm:text-7xl">{f.busynessPct}%</span>
                      <em className="text-3xl sm:text-5xl">{levelLabel(lvl)}</em></>
                  )}
                </div>
                {Number.isFinite(f.scanners) && (
                  <p className="mt-4 text-xs italic opacity-70">{f.scanners} active {f.scanners === 1 ? "scanner" : "scanners"}</p>
                )}
                {!nd && <p className={`mt-3 text-2xl font-medium italic leading-tight sm:text-3xl ${trendTone}`}>{trendLabel}</p>}
                <ul className="mt-12 space-y-2 text-sm sm:text-base">
                  <li>— live capacity: <b className="tabular-nums">{nd ? "?" : f.estimatedPeople}/{f.capacity}</b></li>
                  <li>— estimated wait time: <b>{wait}</b> <em className="opacity-60">{hint}</em></li>
                </ul>
              </div>
            </section>
          );
        })}
      </div>
    </motion.div>
  );
}
