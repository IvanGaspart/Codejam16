import { useEffect, useState } from "react";

export function lastSeenLabel(timestamp: number | null, now: number): string {
  if (!timestamp) return "last seen ages ago...";
  const elapsed = Math.max(0, now - timestamp);
  const date = new Date(timestamp);
  const today = new Date(now);
  const yesterday = new Date(now - 86400000);
  const sameDay = (a: Date, b: Date) => a.toDateString() === b.toDateString();
  if (sameDay(date, today)) {
    return `last seen ${date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", hour12: false })}`;
  }
  if (sameDay(date, yesterday)) return "last seen yesterday";
  if (elapsed < 7 * 86400000) return `last seen ${date.toLocaleDateString([], { weekday: "long" }).toLowerCase()}`;
  return "last seen ages ago...";
}

export function ServerStatus({ online, lastSeen }: { online: boolean; lastSeen: number | null }) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30000);
    return () => window.clearInterval(timer);
  }, []);
  return (
    <div className={`flex items-center gap-2 rounded-full border border-border bg-background/90 px-3 py-2 text-[11px] leading-tight shadow-soft backdrop-blur ${online ? "text-foreground" : "text-muted-foreground"}`}
      role="status" aria-live="polite">
      <span className="text-lg" aria-hidden>{online ? "🌐" : "😴"}</span>
      <span>{online ? "server online" : <>server offline<br /><em>{lastSeenLabel(lastSeen, now)}</em></>}</span>
    </div>
  );
}
