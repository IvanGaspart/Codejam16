"""Predict the current Trottier floor from the connected Wi-Fi BSSID."""

from __future__ import annotations

import argparse
import csv
import re
import sys
from importlib import resources
from pathlib import Path
from typing import Any

from ap_detect import get_current_ap

MAC_RE = re.compile(r"(?<![0-9a-f:])([0-9a-f]{1,2}(?::[0-9a-f]{1,2}){5})(?![0-9a-f:])", re.I)


def norm_bssid(raw: str | None) -> str | None:
    if not raw:
        return None
    m = MAC_RE.search(raw)
    if not m:
        return None
    mac = ":".join(p.zfill(2) for p in m.group(1).lower().split(":"))
    return None if mac == "00:00:00:00:00:00" else mac


def default_lookup_path() -> Path:
    """Packaged floor_ap_lookup.csv shipped with this module."""
    return Path(resources.files("floor_lookup")) / "data" / "floor_ap_lookup.csv"


def load_lookup(path: Path | None = None) -> dict[str, dict[str, Any]]:
    lookup_path = path or default_lookup_path()
    if not lookup_path.exists():
        raise FileNotFoundError(f"lookup table not found: {lookup_path}")
    table: dict[str, dict[str, Any]] = {}
    with lookup_path.open(newline="") as f:
        for row in csv.DictReader(f):
            bssid = norm_bssid(row.get("bssid"))
            if not bssid or "floor" not in row:
                continue
            table[bssid] = row
    return table


def predict_floor(
    *,
    lookup: dict[str, dict[str, Any]] | None = None,
    lookup_path: Path | None = None,
) -> dict[str, Any] | None:
    """Return floor prediction for the currently associated AP, or None if offline.

    On success the dict includes at least ``floor`` and ``bssid``. When the BSSID
    is missing from the lookup, ``floor`` is ``None`` and ``known`` is ``False``.
    """
    table = lookup if lookup is not None else load_lookup(lookup_path)
    ap = get_current_ap()
    if not ap or not ap.get("bssid"):
        return None

    bssid = norm_bssid(ap["bssid"])
    if not bssid:
        return None

    hit = table.get(bssid)
    ssid = ap.get("ssid") or (hit.get("ssid") if hit else None)
    if not hit:
        return {
            "known": False,
            "floor": None,
            "bssid": bssid,
            "ssid": ssid,
        }

    return {
        "known": True,
        "floor": int(hit["floor"]),
        "bssid": bssid,
        "ssid": ssid,
        "confidence": hit.get("confidence") or None,
        "margin_db": hit.get("margin_db") or None,
        "best_rssi_dbm": hit.get("best_rssi_dbm") or None,
    }


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(
        description="Predict Trottier floor from the connected Wi-Fi BSSID (via ap_detect).",
    )
    p.add_argument(
        "--lookup",
        type=Path,
        default=None,
        help="CSV with bssid,floor (default: packaged floor_ap_lookup.csv)",
    )
    args = p.parse_args(argv)

    try:
        table = load_lookup(args.lookup)
    except FileNotFoundError as exc:
        print(exc, file=sys.stderr)
        return 1

    result = predict_floor(lookup=table)
    if result is None:
        print(
            "not connected to Wi-Fi (no BSSID). "
            "On macOS 14+, grant Location Services — see ap_detect README.",
            file=sys.stderr,
        )
        return 2

    print(f"bssid:     {result['bssid']}")
    print(f"ssid:      {result.get('ssid') or '-'}")

    if not result.get("known"):
        print("floor:     unknown (BSSID not in lookup)")
        return 3

    extra = []
    if result.get("confidence"):
        extra.append(f"confidence={result['confidence']}")
    if result.get("margin_db") not in ("", None):
        extra.append(f"margin={result['margin_db']} dB")
    suffix = f"  ({', '.join(extra)})" if extra else ""
    print(f"floor:     {result['floor']}{suffix}")
    return 0
