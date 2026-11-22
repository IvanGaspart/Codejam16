"""Map labelled building APs to BSSIDs via strongest-RSSI polling.

Stand next to a labelled AP, poll nearby Wi-Fi until you stop, and associate
the AP's floor-local ID with the BSSID that was strongest most often.

Uses ``ap_detect.scan_nearby_aps`` (macOS + Linux). Output CSV lands in the
service package root as ``ap_bssid_<building>.csv`` with columns
``ID,floor_number,bssid``. Deduplication runs on exit (most recent wins).
"""

from __future__ import annotations

import argparse
import csv
import signal
import sys
import threading
from collections import Counter
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

from ap_detect import authorize, scan_nearby_aps

BUILDINGS = frozenset(
    {
        "trottier",
        "schulich",
        "mclennan",
        "burnside",
        "rutherford",
        "law",
        "islamic_studies",
    }
)

CSV_FIELDS = ["ID", "floor_number", "bssid"]
DEFAULT_POLL_INTERVAL = 2.0
SERVICE_ROOT = Path(__file__).resolve().parents[2]


@dataclass(frozen=True)
class MappingRow:
    ap_id: str
    floor_number: int
    bssid: str


def output_path(building: str, directory: Path | None = None) -> Path:
    root = directory if directory is not None else SERVICE_ROOT
    return root / f"ap_bssid_{building}.csv"


def strongest_bssid(aps: list[dict]) -> str | None:
    """Return BSSID of the highest-RSSI AP in one scan, or None if empty."""
    if not aps:
        return None
    best = max(aps, key=lambda a: (int(a["rssi"]), a["bssid"]))
    return str(best["bssid"])


def vote_bssid(winners: list[str]) -> str | None:
    """Most-commonly-strongest BSSID; ties broken by first-seen among the tied set."""
    if not winners:
        return None
    counts = Counter(winners)
    top = counts.most_common()
    best_count = top[0][1]
    tied = {bssid for bssid, n in top if n == best_count}
    for bssid in winners:
        if bssid in tied:
            return bssid
    return top[0][0]


def load_csv(path: Path) -> list[MappingRow]:
    if not path.exists() or path.stat().st_size == 0:
        return []
    rows: list[MappingRow] = []
    with path.open(newline="") as f:
        for raw in csv.DictReader(f):
            try:
                rows.append(
                    MappingRow(
                        ap_id=str(raw["ID"]).strip(),
                        floor_number=int(raw["floor_number"]),
                        bssid=str(raw["bssid"]).strip().lower(),
                    )
                )
            except (KeyError, TypeError, ValueError):
                continue
    return rows


def dedupe_rows(rows: list[MappingRow]) -> list[MappingRow]:
    """Keep the most recent row per (ID, floor_number); last occurrence wins."""
    by_key: dict[tuple[str, int], MappingRow] = {}
    for row in rows:
        by_key[(row.ap_id, row.floor_number)] = row
    return sorted(by_key.values(), key=lambda r: (r.floor_number, r.ap_id))


def write_csv(path: Path, rows: list[MappingRow]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=CSV_FIELDS)
        writer.writeheader()
        for row in rows:
            writer.writerow(
                {
                    "ID": row.ap_id,
                    "floor_number": row.floor_number,
                    "bssid": row.bssid,
                }
            )


def flush_mappings(path: Path, session: list[MappingRow]) -> list[MappingRow]:
    """Merge existing file + session (session is more recent) and write unique rows."""
    merged = dedupe_rows([*load_csv(path), *session])
    write_csv(path, merged)
    return merged


def poll_until(
    *,
    stop: threading.Event,
    interval: float,
    on_tick: Callable[[str | None, int], None] | None = None,
) -> list[str]:
    """Poll nearby APs until ``stop`` is set; return strongest BSSID per scan."""
    winners: list[str] = []
    # Run one scan immediately, then wait — so a short cycle still gets data.
    while True:
        aps = scan_nearby_aps()
        bssid = strongest_bssid(aps)
        if bssid:
            winners.append(bssid)
        if on_tick:
            on_tick(bssid, len(aps))
        if stop.wait(interval):
            break
    return winners


def run_polling_cycle(
    *,
    ap_id: str,
    floor: int,
    interval: float,
    wait_for_stop: Callable[[], None],
) -> MappingRow | None:
    """Poll until ``wait_for_stop`` returns; vote the most-common strongest BSSID."""
    stop = threading.Event()
    winners: list[str] = []

    def _ticker(bssid: str | None, n: int) -> None:
        label = bssid or "(none)"
        print(f"  scan: strongest={label}  visible={n}", flush=True)

    def _run() -> None:
        nonlocal winners
        winners = poll_until(stop=stop, interval=interval, on_tick=_ticker)

    thread = threading.Thread(target=_run, daemon=True)
    thread.start()
    try:
        wait_for_stop()
    finally:
        stop.set()
        thread.join(timeout=max(interval, 1.0) + 30)

    bssid = vote_bssid(winners)
    if not bssid:
        print(f"no strongest AP observed for ID={ap_id} floor={floor}", file=sys.stderr)
        return None
    print(
        f"mapped ID={ap_id} floor={floor} → {bssid} "
        f"({Counter(winners)[bssid]}/{len(winners)} strongest scans)",
        flush=True,
    )
    return MappingRow(ap_id=ap_id, floor_number=floor, bssid=bssid)


def _prompt(msg: str) -> str:
    return input(msg).strip()


def _validate_building(raw: str) -> str:
    building = raw.strip().lower().replace(" ", "_")
    if building not in BUILDINGS:
        allowed = ", ".join(sorted(BUILDINGS))
        raise SystemExit(f"unknown building {raw!r}; expected one of: {allowed}")
    return building


def _validate_floor(raw: str | int) -> int:
    try:
        floor = int(raw)
    except (TypeError, ValueError) as exc:
        raise SystemExit(f"floor must be an integer >= 0, got {raw!r}") from exc
    if floor < 0:
        raise SystemExit(f"floor must be >= 0, got {floor}")
    return floor


def _validate_id(raw: str) -> str:
    ap_id = str(raw).strip()
    if not ap_id:
        raise SystemExit("ID must be non-empty")
    return ap_id


def _wait_enter(prompt: str = "Press Enter to stop polling…") -> None:
    try:
        input(prompt)
    except EOFError:
        pass


def oneshot(
    *,
    building: str,
    floor: int,
    ap_id: str,
    interval: float,
    session: list[MappingRow],
) -> None:
    print(
        f"Polling building={building} floor={floor} ID={ap_id}. "
        "Ctrl+C to stop and record.",
        flush=True,
    )
    stop = threading.Event()

    def _on_sig(_signum: int, _frame: object) -> None:
        print("\nStopping…", flush=True)
        stop.set()

    prev = signal.signal(signal.SIGINT, _on_sig)
    try:
        row = run_polling_cycle(
            ap_id=ap_id,
            floor=floor,
            interval=interval,
            wait_for_stop=lambda: stop.wait(),
        )
    finally:
        signal.signal(signal.SIGINT, prev)

    if row:
        session.append(row)


def interactive_session(
    *,
    interval: float,
    out_dir: Path,
    session: list[MappingRow],
) -> Path:
    building = _validate_building(
        _prompt(f"Building id ({', '.join(sorted(BUILDINGS))}): ")
    )
    floor = _validate_floor(_prompt("Floor number: "))
    path = output_path(building, out_dir)
    print(
        f"Writing to {path} on exit. Floor={floor}. Empty ID ends the session.",
        flush=True,
    )
    while True:
        raw_id = _prompt("AP ID (empty to finish session): ")
        if not raw_id:
            break
        ap_id = _validate_id(raw_id)
        print(f"Polling near ID={ap_id}…", flush=True)
        row = run_polling_cycle(
            ap_id=ap_id,
            floor=floor,
            interval=interval,
            wait_for_stop=_wait_enter,
        )
        if row:
            session.append(row)
    return path


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description=(
            "Poll nearby Wi-Fi and map a labelled AP ID to its strongest BSSID. "
            "CSV is written/deduped on exit."
        )
    )
    p.add_argument(
        "building",
        nargs="?",
        help="Building id (trottier, schulich, …)",
    )
    p.add_argument(
        "floor",
        nargs="?",
        type=int,
        help="Floor number (>= 0)",
    )
    p.add_argument(
        "ap_id",
        nargs="?",
        help="Labelled AP ID (foreign key to ap_gps_<building>.csv)",
    )
    p.add_argument(
        "--interactive",
        action="store_true",
        help="Prompt for building/floor once, then ID per polling cycle",
    )
    p.add_argument(
        "--interval",
        type=float,
        default=DEFAULT_POLL_INTERVAL,
        help=f"Seconds between scans (default {DEFAULT_POLL_INTERVAL})",
    )
    p.add_argument(
        "--out-dir",
        type=Path,
        default=SERVICE_ROOT,
        help="Directory for ap_bssid_<building>.csv (default: service/)",
    )
    p.add_argument(
        "--authorize",
        action="store_true",
        help="macOS: trigger Location Services for the WifiInfo helper, then exit",
    )
    return p


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)

    if args.authorize:
        print(authorize())
        return 0

    if args.interval <= 0:
        print("--interval must be > 0", file=sys.stderr)
        return 2

    session: list[MappingRow] = []
    path: Path | None = None

    try:
        if args.interactive:
            if args.building or args.floor is not None or args.ap_id:
                print(
                    "--interactive ignores positional building/floor/ID; "
                    "you will be prompted.",
                    file=sys.stderr,
                )
            path = interactive_session(
                interval=args.interval,
                out_dir=args.out_dir,
                session=session,
            )
        else:
            if args.building is None or args.floor is None or args.ap_id is None:
                print(
                    "usage: map-aps BUILDING FLOOR ID   or   map-aps --interactive",
                    file=sys.stderr,
                )
                return 2
            building = _validate_building(args.building)
            floor = _validate_floor(args.floor)
            ap_id = _validate_id(args.ap_id)
            path = output_path(building, args.out_dir)
            oneshot(
                building=building,
                floor=floor,
                ap_id=ap_id,
                interval=args.interval,
                session=session,
            )
    except KeyboardInterrupt:
        print("\nInterrupted.", flush=True)
    finally:
        if path is not None:
            try:
                merged = flush_mappings(path, session)
                print(
                    f"Wrote {len(merged)} unique row(s) → {path} "
                    f"({len(session)} this session)",
                    flush=True,
                )
            except OSError as exc:
                print(f"failed to write {path}: {exc}", file=sys.stderr)
                return 1
        elif session:
            print(
                "session had mappings but no output path; nothing written",
                file=sys.stderr,
            )
            return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
