#!/usr/bin/env python3
"""Predict the current Trottier floor from the connected Wi-Fi BSSID.

Uses ap_detect (Linux / macOS / Windows) to read the associated AP.

Usage (from repo root):
    uv run --project ap_detect python scripts/predict_floor.py
    uv run --project ap_detect python scripts/predict_floor.py \\
        --lookup data/survey/floor_ap_lookup.csv
"""

from __future__ import annotations

import argparse
import csv
import re
import sys
from pathlib import Path

from ap_detect import get_current_ap

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_LOOKUP = ROOT / "data" / "survey" / "floor_ap_lookup.csv"
MAC_RE = re.compile(r"(?<![0-9a-f:])([0-9a-f]{1,2}(?::[0-9a-f]{1,2}){5})(?![0-9a-f:])", re.I)


def norm_bssid(raw: str | None) -> str | None:
    if not raw:
        return None
    m = MAC_RE.search(raw)
    if not m:
        return None
    mac = ":".join(p.zfill(2) for p in m.group(1).lower().split(":"))
    return None if mac == "00:00:00:00:00:00" else mac


def load_lookup(path: Path) -> dict[str, dict]:
    if not path.exists():
        raise FileNotFoundError(f"lookup table not found: {path}")
    table: dict[str, dict] = {}
    with path.open(newline="") as f:
        for row in csv.DictReader(f):
            bssid = norm_bssid(row.get("bssid"))
            if not bssid or "floor" not in row:
                continue
            table[bssid] = row
    return table


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument(
        "--lookup",
        type=Path,
        default=DEFAULT_LOOKUP,
        help=f"CSV with bssid,floor (default: {DEFAULT_LOOKUP})",
    )
    args = p.parse_args(argv)

    try:
        lookup = load_lookup(args.lookup)
    except FileNotFoundError as exc:
        print(exc, file=sys.stderr)
        return 1

    ap = get_current_ap()
    if not ap or not ap.get("bssid"):
        print(
            "not connected to Wi-Fi (no BSSID). "
            "On macOS 14+, grant Location Services to your terminal "
            "(or WifiInfo.app / Shortcut — see ap_detect).",
            file=sys.stderr,
        )
        return 2

    bssid = norm_bssid(ap["bssid"])
    if not bssid:
        print("could not parse BSSID from ap_detect", file=sys.stderr)
        return 2

    hit = lookup.get(bssid)
    ssid = ap.get("ssid") or (hit.get("ssid") if hit else "-")

    print(f"bssid:     {bssid}")
    print(f"ssid:      {ssid or '-'}")

    if not hit:
        print("floor:     unknown (BSSID not in lookup)")
        return 3

    conf = hit.get("confidence", "")
    margin = hit.get("margin_db", "")
    extra = []
    if conf:
        extra.append(f"confidence={conf}")
    if margin not in ("", None):
        extra.append(f"margin={margin} dB")
    suffix = f"  ({', '.join(extra)})" if extra else ""
    print(f"floor:     {hit['floor']}{suffix}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
