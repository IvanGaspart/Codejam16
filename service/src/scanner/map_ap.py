"""Map labelled building APs to BSSIDs via strongest-RSSI polling.

Stand next to a labelled AP, poll nearby Wi-Fi until you stop, then pick the
AP's BSSID from a ranked list of candidates (the one that was strongest most
often is suggested first).

Uses ``ap_detect.scan_nearby_aps`` (macOS + Linux). macOS only refreshes scan
results every few seconds and returns cached copies in between, so polling is
fast but only *fresh* scans (results that changed) are counted.

By default BSSIDs that differ only in the last hex digit are grouped as one
radio (McGill APs broadcast 5 SSIDs per radio, e.g. ``…:39:50``–``…:39:54``);
the lowest BSSID of the group is stored.

Output CSV lands in the service package root as ``ap_bssid_<building>.csv``
with columns ``ID,floor_number,bssid``. Each mapping is written as soon as it
is saved; rows are deduped per (ID, floor), most recent wins.
"""

from __future__ import annotations

import argparse
import csv
import select
import sys
import threading
import time
from collections.abc import Callable
from dataclasses import dataclass, field
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
DEFAULT_POLL_INTERVAL = 0.5
DEFAULT_TOP = 5
SERVICE_ROOT = Path(__file__).resolve().parents[2]

HELP_TEXT = """\
  <id>        poll near AP <id>; Enter (or Ctrl+C) stops polling
  Enter       use the suggested next ID shown in [brackets]
  f <n>       switch to floor <n>
  ls [all]    list saved mappings for this floor (or every floor)
  q           quit (also Ctrl+D)"""


@dataclass(frozen=True)
class MappingRow:
    ap_id: str
    floor_number: int
    bssid: str


@dataclass(frozen=True)
class PollOptions:
    interval: float = DEFAULT_POLL_INTERVAL
    top: int = DEFAULT_TOP
    max_scans: int | None = None
    max_seconds: float | None = None
    group_radios: bool = True
    assume_yes: bool = False


@dataclass
class Candidate:
    """One radio (or one BSSID with grouping off) aggregated over fresh scans."""

    key: str
    bssids: set[str] = field(default_factory=set)
    ssids: set[str] = field(default_factory=set)
    wins: int = 0
    seen: int = 0
    rssi_sum: int = 0
    rssi_max: int = -999

    @property
    def bssid(self) -> str:
        return min(self.bssids)

    @property
    def mean_rssi(self) -> float:
        return self.rssi_sum / self.seen if self.seen else float("-inf")

    @property
    def label(self) -> str:
        return f"{self.key}x" if len(self.key) == 16 else self.key


def group_key(bssid: str, group_radios: bool = True) -> str:
    """Radio key: BSSID minus its last hex digit (or the BSSID itself)."""
    return bssid[:-1] if group_radios else bssid


def _short(label: str) -> str:
    """Last two octets, e.g. ``44:48:c1:a4:39:5x`` → ``39:5x``."""
    return label[-5:]


class ScanTally:
    """Accumulates fresh scans; ignores repeats of the previous result."""

    def __init__(self, group_radios: bool = True) -> None:
        self.group_radios = group_radios
        self.candidates: dict[str, Candidate] = {}
        self.fresh = 0
        self.polls = 0
        self._last_sig: frozenset[tuple[str, int]] | None = None

    def add_scan(self, aps: list[dict]) -> list[tuple[str, int]] | None:
        """Record one scan; return this scan's (key, rssi) strongest-first, or None if stale/empty."""
        self.polls += 1
        if not aps:
            return None
        sig = frozenset((str(a["bssid"]), int(a["rssi"])) for a in aps)
        if sig == self._last_sig:
            return None  # cached copy of the previous scan
        self._last_sig = sig
        self.fresh += 1

        best: dict[str, int] = {}
        for ap in aps:
            bssid = str(ap["bssid"]).lower()
            rssi = int(ap["rssi"])
            key = group_key(bssid, self.group_radios)
            cand = self.candidates.setdefault(key, Candidate(key))
            cand.bssids.add(bssid)
            if ap.get("ssid"):
                cand.ssids.add(str(ap["ssid"]))
            best[key] = max(best.get(key, -999), rssi)

        for key, rssi in best.items():
            cand = self.candidates[key]
            cand.seen += 1
            cand.rssi_sum += rssi
            cand.rssi_max = max(cand.rssi_max, rssi)
        ordered = sorted(best.items(), key=lambda kv: (-kv[1], kv[0]))
        self.candidates[ordered[0][0]].wins += 1
        return ordered

    def ranked(self) -> list[Candidate]:
        """Most often strongest first; ties by mean RSSI, then key."""
        return sorted(
            self.candidates.values(),
            key=lambda c: (-c.wins, -c.mean_rssi, c.key),
        )


def output_path(building: str, directory: Path | None = None) -> Path:
    root = directory if directory is not None else SERVICE_ROOT
    return root / f"ap_bssid_{building}.csv"


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
    return sorted(by_key.values(), key=lambda r: (r.floor_number, _id_sort_key(r.ap_id)))


def _id_sort_key(ap_id: str) -> tuple[int, int, str]:
    return (0, int(ap_id), "") if ap_id.isdigit() else (1, 0, ap_id)


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


def next_id(ap_id: str) -> str | None:
    """Suggest the ID after ``ap_id`` when it ends in a number (12 → 13, B3 → B4)."""
    digits = len(ap_id) - len(ap_id.rstrip("0123456789"))
    if digits == 0:
        return None
    head, num = ap_id[:-digits], ap_id[-digits:]
    return f"{head}{int(num) + 1:0{digits}d}"


def poll_until(
    *,
    stop: threading.Event,
    opts: PollOptions,
    on_fresh: Callable[[ScanTally, list[tuple[str, int]], int], None] | None = None,
    scan: Callable[[], list[dict]] = scan_nearby_aps,
) -> ScanTally:
    """Poll nearby APs until ``stop`` is set or a limit in ``opts`` is hit."""
    tally = ScanTally(group_radios=opts.group_radios)
    started = time.monotonic()
    while True:
        aps = scan()
        ordered = tally.add_scan(aps)
        if ordered is not None and on_fresh:
            on_fresh(tally, ordered, len(aps))
        elapsed = time.monotonic() - started
        if (opts.max_scans is not None and tally.fresh >= opts.max_scans) or (
            opts.max_seconds is not None and elapsed >= opts.max_seconds
        ):
            stop.set()
        if stop.wait(opts.interval):
            break
    return tally


def _wait_for_enter(stop: threading.Event) -> None:
    """Return on Enter, Ctrl+C, EOF, or once ``stop`` is set by the poller."""
    try:
        while not stop.is_set():
            ready, _, _ = select.select([sys.stdin], [], [], 0.2)
            if ready:
                sys.stdin.readline()
                return
    except KeyboardInterrupt:
        print()


def run_polling_cycle(*, ap_id: str, floor: int, opts: PollOptions) -> ScanTally:
    stop = threading.Event()
    started = time.monotonic()
    result: list[ScanTally] = []

    def _ticker(tally: ScanTally, ordered: list[tuple[str, int]], n: int) -> None:
        tops = "  ".join(
            f"{_short(tally.candidates[k].label)} {rssi}" for k, rssi in ordered[:3]
        )
        leader = tally.ranked()[0]
        print(
            f"  scan {tally.fresh:>2}  +{time.monotonic() - started:4.0f}s  "
            f"{n:>3} APs | {tops} | leader {_short(leader.label)} "
            f"({leader.wins}/{tally.fresh})",
            flush=True,
        )

    def _run() -> None:
        result.append(poll_until(stop=stop, opts=opts, on_fresh=_ticker))

    limits = []
    if opts.max_scans is not None:
        limits.append(f"{opts.max_scans} scans")
    if opts.max_seconds is not None:
        limits.append(f"{opts.max_seconds:g}s")
    until = f"auto-stops after {' or '.join(limits)}, " if limits else ""
    print(
        f"Polling near ID={ap_id} floor={floor} ({until}Enter to stop). "
        "Fresh scans arrive every few seconds…",
        flush=True,
    )
    thread = threading.Thread(target=_run, daemon=True)
    thread.start()
    try:
        _wait_for_enter(stop)
    finally:
        stop.set()
        thread.join(timeout=opts.interval + 60)
    return result[0] if result else ScanTally(opts.group_radios)


def print_candidates(tally: ScanTally, top: int) -> list[Candidate]:
    ranked = tally.ranked()[:top]
    print(f"\nTop {len(ranked)} of {len(tally.candidates)} after {tally.fresh} fresh scan(s):")
    print(f"  {'#':>2}  {'bssid':<18} {'strongest':>9} {'seen':>7} {'avg':>6} {'max':>4}  ssids")
    for i, c in enumerate(ranked, 1):
        ssids = ",".join(sorted(c.ssids)) or "-"
        if len(ssids) > 40:
            ssids = f"{len(c.ssids)} ssids"
        print(
            f"  {i:>2}  {c.label:<18} {f'{c.wins}/{tally.fresh}':>9} "
            f"{f'{c.seen}/{tally.fresh}':>7} {c.mean_rssi:6.1f} {c.rssi_max:>4}  {ssids}"
        )
    return ranked


def choose_candidate(ranked: list[Candidate], assume_yes: bool) -> Candidate | str | None:
    """Return the picked candidate, ``"retry"``, or None to skip."""
    if assume_yes or not sys.stdin.isatty():
        return ranked[0]
    while True:
        try:
            raw = input(f"Save which? [1-{len(ranked)}, Enter=1, r=retry, s=skip]: ")
        except EOFError:
            return ranked[0]
        raw = raw.strip().lower()
        if raw == "":
            return ranked[0]
        if raw in {"r", "retry"}:
            return "retry"
        if raw in {"s", "skip"}:
            return None
        if raw.isdigit() and 1 <= int(raw) <= len(ranked):
            return ranked[int(raw) - 1]
        print("  ?", flush=True)


class MappingSession:
    """Holds the output file and saves each mapping immediately."""

    def __init__(self, building: str, path: Path) -> None:
        self.building = building
        self.path = path
        self.rows = dedupe_rows(load_csv(path))
        self.saved = 0

    def existing(self, ap_id: str, floor: int) -> MappingRow | None:
        return next(
            (r for r in self.rows if r.ap_id == ap_id and r.floor_number == floor), None
        )

    def others_with(self, bssid: str, ap_id: str, floor: int) -> list[MappingRow]:
        return [
            r
            for r in self.rows
            if r.bssid == bssid and (r.ap_id, r.floor_number) != (ap_id, floor)
        ]

    def save(self, row: MappingRow) -> None:
        self.rows = flush_mappings(self.path, [row])
        self.saved += 1

    def list(self, floor: int | None) -> None:
        rows = [r for r in self.rows if floor is None or r.floor_number == floor]
        where = "all floors" if floor is None else f"floor {floor}"
        print(f"{len(rows)} mapping(s) on {where} in {self.path.name}:")
        for r in rows:
            print(f"  floor {r.floor_number:>2}  ID {r.ap_id:<6} {r.bssid}")


def map_one(session: MappingSession, *, ap_id: str, floor: int, opts: PollOptions) -> bool:
    """Poll, show candidates and save the picked one. Return True if saved."""
    prev = session.existing(ap_id, floor)
    if prev:
        print(f"note: ID={ap_id} floor={floor} is already mapped to {prev.bssid}; saving replaces it")
    while True:
        tally = run_polling_cycle(ap_id=ap_id, floor=floor, opts=opts)
        if not tally.candidates:
            print(f"no APs observed for ID={ap_id} floor={floor}", file=sys.stderr)
            return False
        ranked = print_candidates(tally, opts.top)
        picked = choose_candidate(ranked, opts.assume_yes)
        if picked == "retry":
            continue
        if picked is None:
            print("skipped", flush=True)
            return False
        assert isinstance(picked, Candidate)
        row = MappingRow(ap_id=ap_id, floor_number=floor, bssid=picked.bssid)
        for other in session.others_with(row.bssid, ap_id, floor):
            print(
                f"warning: {row.bssid} is also mapped to ID={other.ap_id} "
                f"floor={other.floor_number}",
                file=sys.stderr,
            )
        session.save(row)
        print(f"saved ID={ap_id} floor={floor} → {row.bssid}  ({session.path})", flush=True)
        return True


def mapping_loop(
    session: MappingSession, *, floor: int, opts: PollOptions, first_id: str | None = None
) -> None:
    print(f"Mapping {session.building} → {session.path}. Type 'h' for commands.", flush=True)
    suggestion = first_id
    while True:
        hint = f" [{suggestion}]" if suggestion else ""
        try:
            raw = input(f"[{session.building} f{floor}] AP ID{hint}: ").strip()
        except EOFError:
            print()
            return
        cmd, _, arg = raw.partition(" ")
        cmd = cmd.lower()
        if cmd in {"q", "quit", "exit"}:
            return
        if cmd in {"h", "?", "help"}:
            print(HELP_TEXT)
            continue
        if cmd in {"f", "floor"}:
            try:
                floor = _validate_floor(arg)
            except SystemExit as exc:
                print(exc)
            continue
        if cmd in {"ls", "list"}:
            session.list(None if arg.strip() == "all" else floor)
            continue
        ap_id = raw or suggestion
        if not ap_id:
            continue
        map_one(session, ap_id=ap_id, floor=floor, opts=opts)
        suggestion = next_id(ap_id)


def _prompt(msg: str) -> str:
    return input(msg).strip()


def _validate_building(raw: str) -> str:
    building = raw.strip().lower().replace(" ", "_")
    if building in BUILDINGS:
        return building
    matches = sorted(b for b in BUILDINGS if b.startswith(building)) if building else []
    if len(matches) == 1:
        return matches[0]
    allowed = ", ".join(sorted(BUILDINGS))
    raise SystemExit(f"unknown building {raw!r}; expected one of: {allowed}")


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


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description=(
            "Poll nearby Wi-Fi and map a labelled AP ID to its BSSID. "
            "With BUILDING FLOOR ID: map one AP and exit. Without ID: prompt "
            "for IDs in a loop (missing building/floor are prompted too)."
        ),
        epilog=(
            "examples:\n"
            "  map-aps                      prompt for everything\n"
            "  map-aps trottier 3           loop over IDs on Trottier floor 3\n"
            "  map-aps trot 3 12            map ID 12 once (building prefix ok)\n"
            "  map-aps trottier 3 -n 4 -y   auto-stop after 4 fresh scans, save #1\n"
            "  map-aps trottier --list      show saved mappings"
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    p.add_argument("building", nargs="?", help="Building id or unique prefix (trottier, schulich, …)")
    p.add_argument("floor", nargs="?", type=int, help="Floor number (>= 0)")
    p.add_argument("ap_id", nargs="?", help="Labelled AP ID (foreign key to ap_gps_<building>.csv)")
    p.add_argument(
        "-i",
        "--interactive",
        action="store_true",
        help="Keep prompting for IDs even when ID is given",
    )
    p.add_argument(
        "-n",
        "--scans",
        type=int,
        help="Stop polling automatically after N fresh scans",
    )
    p.add_argument(
        "-t",
        "--seconds",
        type=float,
        help="Stop polling automatically after N seconds",
    )
    p.add_argument(
        "-k",
        "--top",
        type=int,
        default=DEFAULT_TOP,
        help=f"Candidates to show per cycle (default {DEFAULT_TOP})",
    )
    p.add_argument(
        "-y",
        "--yes",
        action="store_true",
        help="Save the top candidate without asking",
    )
    p.add_argument(
        "--no-group",
        action="store_true",
        help="Rank individual BSSIDs instead of grouping sibling SSIDs per radio",
    )
    p.add_argument(
        "--interval",
        type=float,
        default=DEFAULT_POLL_INTERVAL,
        help=(
            f"Seconds between polls (default {DEFAULT_POLL_INTERVAL}); macOS "
            "itself only refreshes results every few seconds"
        ),
    )
    p.add_argument(
        "--out-dir",
        type=Path,
        default=SERVICE_ROOT,
        help="Directory for ap_bssid_<building>.csv (default: service/)",
    )
    p.add_argument("--list", action="store_true", help="Print saved mappings and exit")
    p.add_argument(
        "--authorize",
        action="store_true",
        help="macOS: trigger Location Services for the WifiInfo helper, then exit",
    )
    return p


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    if args.authorize:
        print(authorize())
        return 0

    if args.interval <= 0:
        parser.error("--interval must be > 0")
    if args.top < 1:
        parser.error("--top must be >= 1")
    if args.scans is not None and args.scans < 1:
        parser.error("--scans must be >= 1")
    if args.seconds is not None and args.seconds <= 0:
        parser.error("--seconds must be > 0")

    opts = PollOptions(
        interval=args.interval,
        top=args.top,
        max_scans=args.scans,
        max_seconds=args.seconds,
        group_radios=not args.no_group,
        assume_yes=args.yes,
    )

    session: MappingSession | None = None
    try:
        building = _validate_building(
            args.building or _prompt(f"Building ({', '.join(sorted(BUILDINGS))}): ")
        )
        session = MappingSession(building, output_path(building, args.out_dir))
        if args.list:
            session.list(args.floor)
            return 0
        floor = _validate_floor(args.floor if args.floor is not None else _prompt("Floor: "))

        if args.ap_id and not args.interactive:
            ok = map_one(session, ap_id=_validate_id(args.ap_id), floor=floor, opts=opts)
            return 0 if ok else 1
        mapping_loop(session, floor=floor, opts=opts, first_id=args.ap_id)
    except (KeyboardInterrupt, EOFError):
        print("\nInterrupted.", flush=True)
    finally:
        if session is not None and session.saved:
            print(
                f"{session.saved} mapping(s) saved this session; "
                f"{len(session.rows)} unique row(s) in {session.path}",
                flush=True,
            )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
