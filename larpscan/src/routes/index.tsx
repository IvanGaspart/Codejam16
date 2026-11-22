import { useQueries, useQuery } from "@tanstack/react-query";
import { ClientOnly, createFileRoute } from "@tanstack/react-router";
import { AnimatePresence, MotionConfig, motion } from "framer-motion";
import { SlidersHorizontal, X } from "lucide-react";
import { Suspense, lazy, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  MOCK_BUILDINGS, buildingBusyness, fetchBuildings, fetchFloors, loadSnapshot, matchesSearch, mergeLibraryBuildings,
  mockFloors, placeholderFloors, saveSnapshot, sortForList, type FloorEntry, type FloorSort, type LiveSnapshot,
} from "@/lib/larp";
import { useIsMobile } from "@/hooks/use-mobile";
import { Splash } from "@/components/larp/Splash";
import { ThemeToggle } from "@/components/larp/ThemeToggle";
import { FloorRow } from "@/components/larp/FloorRow";
import { FloorView } from "@/components/larp/FloorView";
import { ServerStatus } from "@/components/larp/ServerStatus";

const BuildingMap = lazy(() => import("@/components/larp/BuildingMap"));

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "larp — live building busyness" },
      { name: "description", content: "find a quiet floor: live busyness for every floor of the Trottier Building." },
      { property: "og:title", content: "larp — live building busyness" },
      { property: "og:description", content: "find a quiet floor: live busyness for every floor of the Trottier Building." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: Index,
});

function useSlowConnection(latencyMs: number | null) {
  const [slow, setSlow] = useState(false);
  useEffect(() => {
    const c = (navigator as Navigator & { connection?: { effectiveType?: string; saveData?: boolean } }).connection;
    const bad = !!c && (c.saveData === true || ["slow-2g", "2g"].includes(c.effectiveType ?? ""));
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    setSlow(bad || reduce || (latencyMs !== null && latencyMs > 4000));
  }, [latencyMs]);
  return slow;
}

function Index() {
  const isMobile = useIsMobile();
  const [ready, setReady] = useState(false);
  const [useMock, setUseMock] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [maxPct, setMaxPct] = useState(100);
  const [sortMode, setSortMode] = useState<FloorSort>("floor");
  const [showFilter, setShowFilter] = useState(false);
  const [open, setOpen] = useState<{ buildingId: string; floor: number; originTop: number } | null>(null);
  const [latency, setLatency] = useState<number | null>(null);
  const [snapshot, setSnapshot] = useState<LiveSnapshot | null>(null);
  const [lastSeen, setLastSeen] = useState<number | null>(null);
  const [serverOnline, setServerOnline] = useState(false);
  const panelRef = useRef<HTMLDivElement>(null);
  const slow = useSlowConnection(latency);
  const onSplashDone = useCallback(() => setReady(true), []);

  useEffect(() => {
    const t = localStorage.getItem("larp-theme");
    if (t === "dark") document.documentElement.classList.add("dark");
    setSnapshot(loadSnapshot());
    const seen = Number(localStorage.getItem("larp-server-last-seen"));
    if (seen > 0) setLastSeen(seen);
    const onKey = (event: KeyboardEvent) => {
      if (event.altKey && event.code === "KeyP") {
        event.preventDefault();
        setUseMock((value) => !value);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  const buildingsQ = useQuery({
    queryKey: ["buildings", useMock],
    queryFn: () => (useMock ? Promise.resolve(MOCK_BUILDINGS) : fetchBuildings()),
    refetchInterval: useMock ? false : 15000,
    retry: 6, retryDelay: (n) => Math.min(2000 * 2 ** n, 15000),
  });
  const remoteBuildings = buildingsQ.data ?? (buildingsQ.isError ? snapshot?.buildings : undefined);
  const buildings = useMemo(
    () => mergeLibraryBuildings(useMock ? MOCK_BUILDINGS : remoteBuildings),
    [useMock, remoteBuildings],
  );
  const remoteBuildingIds = useMemo(() => new Set(remoteBuildings?.map((b) => b.id) ?? []), [remoteBuildings]);
  const floorQs = useQueries({
    queries: buildings.map((b) => {
      const remote = useMock || remoteBuildingIds.has(b.id);
      return {
        queryKey: ["floors", b.id, useMock, remote],
        queryFn: async () => {
          if (useMock) return mockFloors(b.id);
          if (!remote) return placeholderFloors(b.id);
          const t0 = performance.now();
          const r = await fetchFloors(b.id);
          setLatency(performance.now() - t0);
          return r;
        },
        refetchInterval: useMock ? 15000 : remote ? 15000 : false,
        retry: remote ? 3 : 0,
      };
    }),
  });

  const allFloors: FloorEntry[] = useMemo(
    () => buildings.flatMap((b, i) => {
      const query = floorQs[i];
      const floors = query?.data ?? (!useMock && query?.isError ? snapshot?.floors[b.id] ?? [] : []);
      return floors.map((f) => ({ ...f, buildingId: b.id, buildingName: b.name }));
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [buildings, floorQs.map((q) => `${q.dataUpdatedAt}-${q.isError}`).join(","), snapshot, useMock],
  );
  const mapBuildings = useMemo(
    () => buildings.map((b) => ({ ...b, ...buildingBusyness(allFloors.filter((f) => f.buildingId === b.id)) })),
    [buildings, allFloors],
  );

  const floorStamps = floorQs.map((q) => q.dataUpdatedAt).join(",");
  useEffect(() => {
    if (useMock || !buildingsQ.data?.length || !floorQs.length || !floorQs.every((q) => q.data)) return;
    const next: LiveSnapshot = {
      savedAt: Date.now(),
      buildings: buildingsQ.data,
      floors: Object.fromEntries(buildingsQ.data.map((b, i) => [b.id, floorQs[i]!.data!])),
    };
    setSnapshot(next);
    saveSnapshot(next);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [useMock, buildingsQ.dataUpdatedAt, floorStamps]);

  useEffect(() => {
    if (useMock) return;
    if (buildingsQ.isSuccess) setServerOnline(true);
    else if (buildingsQ.isError) setServerOnline(false);
  }, [buildingsQ.isSuccess, buildingsQ.isError, useMock]);

  useEffect(() => {
    if (useMock || !buildingsQ.dataUpdatedAt) return;
    setLastSeen(buildingsQ.dataUpdatedAt);
    try { localStorage.setItem("larp-server-last-seen", String(buildingsQ.dataUpdatedAt)); } catch { /* storage may be unavailable */ }
  }, [buildingsQ.dataUpdatedAt, useMock]);

  const searching = query.trim() !== "" || maxPct < 100;
  const expanded = selectedId !== null || searching;
  const listed = sortForList(
    allFloors.filter((f) => (selectedId ? f.buildingId === selectedId : true) && matchesSearch(f, query, maxPct)),
    sortMode,
  );
  const loading = buildingsQ.isLoading || floorQs.some((q) => q.isLoading);
  const error = !useMock && (buildingsQ.isError || floorQs.some((q) => q.isError));

  const openFloor = (f: FloorEntry) => {
    const top = panelRef.current?.getBoundingClientRect().top ?? window.innerHeight / 2;
    setOpen({ buildingId: f.buildingId, floor: f.floor, originTop: Math.min(0.95, Math.max(0, top / window.innerHeight)) });
  };
  const selectFromMap = (id: string | null) => {
    if (open && !isMobile) setOpen(null);
    setSelectedId(id);
  };

  return (
    <MotionConfig reducedMotion={slow ? "always" : "never"}>
      <Splash onDone={onSplashDone} />
      <main className="relative flex h-dvh flex-col overflow-hidden bg-background text-foreground md:flex-row">
        <div className="absolute left-3 top-3 z-20">
          <ServerStatus online={serverOnline} lastSeen={lastSeen} />
        </div>
        <div className="absolute right-3 top-3 z-20"><ThemeToggle /></div>

        <motion.div className="relative isolate z-0 min-h-0 flex-1 md:order-2"
          initial={{ opacity: 0, scale: 1.04 }} animate={ready ? { opacity: 1, scale: 1 } : {}}
          transition={{ duration: 0.9, ease: [0.22, 1, 0.36, 1] }}>
          <ClientOnly fallback={<div className="h-full w-full bg-muted" />}>
            <Suspense fallback={<div className="h-full w-full bg-muted" />}>
              <BuildingMap buildings={mapBuildings} selectedId={selectedId} onSelect={selectFromMap} />
            </Suspense>
          </ClientOnly>
        </motion.div>

        <div className={`relative z-10 flex flex-col md:order-1 md:h-full md:shrink-0 md:border-r md:border-border md:bg-background md:transition-[width] md:duration-500 ${expanded ? "md:w-1/2" : "md:w-1/3"}`}>
          <div className="relative -mt-8 px-4 md:mt-0 md:px-5 md:pb-4 md:pt-20">
            <div className="mx-auto max-w-2xl rounded-2xl border border-border bg-background/90 shadow-soft backdrop-blur md:mx-0 md:max-w-none md:bg-background md:shadow-none">
              <div className="flex items-center gap-3 px-5 py-3">
                <span className="text-xl font-bold text-accent">&gt;</span>
                <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="where we larping at"
                  className="min-w-0 flex-1 bg-transparent text-base italic outline-none placeholder:text-muted-foreground sm:text-lg" />
                {query && <button aria-label="clear search" onClick={() => setQuery("")}><X className="h-4 w-4 text-muted-foreground" /></button>}
                <button aria-label="filter by busyness" aria-expanded={showFilter} onClick={() => setShowFilter((s) => !s)}
                  className={`rounded-md p-1.5 transition-colors ${showFilter || maxPct < 100 ? "bg-foreground text-background" : "text-muted-foreground hover:text-foreground"}`}>
                  <SlidersHorizontal className="h-4 w-4" />
                </button>
              </div>
              <AnimatePresence initial={false}>
                {showFilter && (
                  <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: "auto", opacity: 1 }} exit={{ height: 0, opacity: 0 }}
                    className="overflow-hidden">
                    <div className="flex items-center justify-between gap-3 border-t border-border px-5 py-3 text-xs text-muted-foreground">
                      <span>sort by</span>
                      <div className="flex rounded-md border border-border p-0.5" role="group" aria-label="floor sorting">
                        {(["floor", "busyness"] as const).map((mode) => (
                          <button key={mode} aria-pressed={sortMode === mode} onClick={() => setSortMode(mode)}
                            className={`rounded px-2.5 py-1 transition-colors ${sortMode === mode ? "bg-foreground text-background" : "hover:text-foreground"}`}>
                            {mode === "floor" ? "floor ↓" : "busyness"}
                          </button>
                        ))}
                      </div>
                    </div>
                    <label className="flex items-center gap-4 border-t border-border px-5 py-3 text-xs text-muted-foreground">
                      <span className="whitespace-nowrap">busyness ≤ <b className="tabular-nums text-foreground">{maxPct}%</b></span>
                      <input type="range" min={0} max={100} step={5} value={maxPct} onChange={(e) => setMaxPct(Number(e.target.value))}
                        className="w-full accent-foreground" />
                    </label>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          </div>

          <motion.div ref={panelRef} className="relative -mt-8 overflow-hidden md:mt-0 md:min-h-0 md:flex-1"
            animate={{ height: isMobile ? (expanded ? "46dvh" : "5.5rem") : "auto" }} transition={{ type: "spring", stiffness: 220, damping: 30 }}>
            <div className="no-scrollbar h-full overflow-y-auto pt-8 md:pt-0">
              {error && (
                <div className="border-b border-border px-6 py-2 text-center text-xs text-muted-foreground">
                  {snapshot ? "showing the last live snapshot · still trying…" : "can't reach the server yet — still trying…"}
                </div>
              )}
              {!expanded ? (
                <p className="px-6 py-3 text-center text-xs text-muted-foreground">
                  {loading ? <span className="animate-pulse">waking up the server… this can take ~40s</span> : "tap a building or start searching"}
                </p>
              ) : listed.length === 0 ? (
                <p className="px-6 py-8 text-center text-sm text-muted-foreground">{loading ? <span className="animate-pulse">loading floors…</span> : "no floors match"}</p>
              ) : (
                <AnimatePresence initial={false}>
                  {listed.map((f) => <FloorRow key={`${f.buildingId}-${f.floor}`} f={f} onOpen={() => openFloor(f)} />)}
                </AnimatePresence>
              )}
            </div>
          </motion.div>
        </div>

        <AnimatePresence>
          {open && (
            <FloorView key="floor" floors={allFloors.filter((f) => f.buildingId === open.buildingId)}
              initialFloor={open.floor} originTop={open.originTop} sortMode={sortMode} preview={useMock} onBack={() => setOpen(null)} />
          )}
        </AnimatePresence>
      </main>
    </MotionConfig>
  );
}
