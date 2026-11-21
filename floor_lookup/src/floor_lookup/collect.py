"""Collect visible McGill Wi-Fi APs at the current floor (Linux survey walk)."""

from __future__ import annotations

import argparse
import csv
import re
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

# Survey CSVs land under repo /data/ (gitignored on main).
_REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_OUT = _REPO_ROOT / "data" / "survey" / "observations.csv"
DEFAULT_LOCATIONS = _REPO_ROOT / "data" / "survey" / "ap_locations.csv"

INTERESTING_SSIDS = {
    "wpa.mcgill.ca",
    "eduroam",
    "guest.mcgill.ca",
    "mywifidevice.mcgill.ca",
    "managed.mcgill.ca",
}

OBS_FIELDS = [
    "building",
    "floor",
    "bssid",
    "ssid",
    "rssi_dbm",
    "freq_mhz",
    "connected",
    "proximity",
    "collected_at",
]

LOC_FIELDS = [
    "building",
    "bssid",
    "ssid",
    "floor",
    "rssi_dbm",
    "freq_mhz",
    "n_observations",
]


def proximity(rssi: float) -> str:
    if rssi >= -50:
        return "very_close"
    if rssi >= -55:
        return "close"
    if rssi >= -60:
        return "near"
    if rssi >= -65:
        return "likely_same_area"
    return "distant"


def _run(cmd: list[str]) -> str:
    return subprocess.check_output(cmd, text=True, stderr=subprocess.DEVNULL)


def detect_iface() -> str:
    out = _run(["iw", "dev"])
    match = re.search(r"Interface\s+(\S+)", out)
    if not match:
        raise RuntimeError("no Wi-Fi interface found (iw dev)")
    return match.group(1)


def connected_info(iface: str) -> tuple[str | None, float | None]:
    link = _run(["iw", "dev", iface, "link"])
    bssid_m = re.search(r"Connected to ([0-9a-f:]{17})", link, re.I)
    rssi_m = re.search(r"signal:\s*([-0-9.]+)", link)
    bssid = bssid_m.group(1).lower() if bssid_m else None
    rssi = float(rssi_m.group(1)) if rssi_m else None
    return bssid, rssi


def refresh_scan(iface: str) -> None:
    try:
        subprocess.run(
            ["nmcli", "device", "wifi", "rescan", "ifname", iface],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=15,
        )
    except (OSError, subprocess.SubprocessError):
        pass
    time.sleep(2.5)


def scan_aps(iface: str, min_rssi: float) -> list[dict]:
    refresh_scan(iface)
    dump = _run(["iw", "dev", iface, "scan", "dump"])
    connected_bssid, connected_rssi = connected_info(iface)

    aps: list[dict] = []
    cur: dict | None = None
    for line in dump.splitlines():
        if line.startswith("BSS "):
            if cur and cur.get("bssid"):
                aps.append(cur)
            m = re.match(r"BSS ([0-9a-f:]{17})", line, re.I)
            cur = {
                "bssid": m.group(1).lower() if m else None,
                "associated": "associated" in line,
            }
        elif cur is None:
            continue
        elif "signal:" in line and "rssi_dbm" not in cur:
            m = re.search(r"signal:\s*([-0-9.]+)", line)
            if m:
                cur["rssi_dbm"] = float(m.group(1))
        elif "SSID:" in line:
            ssid = line.split("SSID:", 1)[1].strip()
            if ssid or "ssid" not in cur:
                cur["ssid"] = ssid
        elif "freq:" in line and "freq_mhz" not in cur:
            m = re.search(r"freq:\s*([0-9.]+)", line)
            if m:
                cur["freq_mhz"] = float(m.group(1))
    if cur and cur.get("bssid"):
        aps.append(cur)

    for ap in aps:
        if ap["bssid"] == connected_bssid and connected_rssi is not None:
            ap["rssi_dbm"] = connected_rssi
            ap["associated"] = True

    best: dict[str, dict] = {}
    for ap in aps:
        if ap.get("ssid") not in INTERESTING_SSIDS or "rssi_dbm" not in ap:
            continue
        if ap["rssi_dbm"] < min_rssi:
            continue
        prev = best.get(ap["bssid"])
        if prev is None or ap["rssi_dbm"] > prev["rssi_dbm"]:
            best[ap["bssid"]] = ap

    rows = sorted(best.values(), key=lambda a: (-a["rssi_dbm"], a["bssid"]))
    for ap in rows:
        ap["connected"] = ap["bssid"] == connected_bssid
    return rows


def append_observations(
    path: Path,
    *,
    building: str,
    floor: int,
    aps: list[dict],
    collected_at: str,
) -> int:
    path.parent.mkdir(parents=True, exist_ok=True)
    write_header = not path.exists() or path.stat().st_size == 0
    with path.open("a", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=OBS_FIELDS)
        if write_header:
            writer.writeheader()
        for ap in aps:
            rssi = ap["rssi_dbm"]
            writer.writerow(
                {
                    "building": building,
                    "floor": floor,
                    "bssid": ap["bssid"],
                    "ssid": ap["ssid"],
                    "rssi_dbm": int(rssi) if float(rssi).is_integer() else rssi,
                    "freq_mhz": int(ap.get("freq_mhz") or 0),
                    "connected": "true" if ap.get("connected") else "false",
                    "proximity": proximity(float(rssi)),
                    "collected_at": collected_at,
                }
            )
    return len(aps)


def reduce_locations(observations: Path, locations: Path) -> int:
    if not observations.exists():
        return 0

    best: dict[str, dict] = {}
    counts: dict[str, int] = {}
    with observations.open(newline="") as f:
        for row in csv.DictReader(f):
            bssid = row["bssid"].lower()
            counts[bssid] = counts.get(bssid, 0) + 1
            rssi = float(row["rssi_dbm"])
            prev = best.get(bssid)
            if prev is None or rssi > float(prev["rssi_dbm"]):
                best[bssid] = row

    locations.parent.mkdir(parents=True, exist_ok=True)
    rows = sorted(
        best.values(),
        key=lambda r: (int(r["floor"]), -float(r["rssi_dbm"]), r["bssid"]),
    )
    with locations.open("w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=LOC_FIELDS)
        writer.writeheader()
        for row in rows:
            writer.writerow(
                {
                    "building": row["building"],
                    "bssid": row["bssid"],
                    "ssid": row["ssid"],
                    "floor": row["floor"],
                    "rssi_dbm": row["rssi_dbm"],
                    "freq_mhz": row["freq_mhz"],
                    "n_observations": counts[row["bssid"]],
                }
            )
    return len(rows)


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(
        description="Collect visible McGill APs at a floor into data/survey/ (Linux).",
    )
    p.add_argument("--floor", type=int, help="Floor number (e.g. 0)")
    p.add_argument("--building", default="trottier")
    p.add_argument("--min-rssi", type=float, default=-65.0)
    p.add_argument("--out", type=Path, default=DEFAULT_OUT)
    p.add_argument("--locations", type=Path, default=DEFAULT_LOCATIONS)
    p.add_argument("--reduce-only", action="store_true")
    args = p.parse_args(argv)
    if not args.reduce_only and args.floor is None:
        p.error("--floor is required unless --reduce-only")

    if args.reduce_only:
        n = reduce_locations(args.out, args.locations)
        print(f"reduced {n} AP locations → {args.locations}")
        return 0

    try:
        iface = detect_iface()
        aps = scan_aps(iface, args.min_rssi)
    except (OSError, subprocess.SubprocessError, RuntimeError) as exc:
        print(f"scan failed: {exc}", file=sys.stderr)
        return 1

    if not aps:
        print("no APs at/above min RSSI", file=sys.stderr)
        return 1

    collected_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    n = append_observations(
        args.out,
        building=args.building,
        floor=args.floor,
        aps=aps,
        collected_at=collected_at,
    )
    n_loc = reduce_locations(args.out, args.locations)
    connected = next((a for a in aps if a.get("connected")), None)
    print(
        f"appended {n} rows @ floor {args.floor} → {args.out}"
        + (
            f" (connected {connected['bssid']} {connected['rssi_dbm']} dBm)"
            if connected
            else ""
        )
    )
    print(f"reduced {n_loc} AP locations → {args.locations}")
    return 0
