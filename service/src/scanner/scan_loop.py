"""Main scanner loop: periodic floor prediction + BLE sightings.

Yields updates shaped for the server batch DTO (bssid + sightings).
HTTP posting is left to a later step (#44).
"""

from __future__ import annotations

import argparse
import asyncio
import json
import signal
import sys
from collections.abc import AsyncIterator
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

from .ble import RSSI_FLOOR, Sighting, SightingBuffer, open_scanner
from .predict import predict_floor

BATCH_INTERVAL = 15.0


@dataclass(frozen=True)
class ScanUpdate:
    """One interval's observation: predicted floor + BLE devices."""

    at: datetime
    floor: dict[str, Any] | None
    devices: list[Sighting]

    @property
    def device_count(self) -> int:
        return len(self.devices)


async def run(
    *,
    interval: float = BATCH_INTERVAL,
    min_rssi: int = RSSI_FLOOR,
) -> AsyncIterator[ScanUpdate]:
    """Continuously scan BLE; every ``interval`` seconds yield floor + devices.

    The BLE buffer is cleared after each yield. ``floor`` is ``None`` when
    offline (no associated AP); a REST poster should skip that tick.
    """
    if interval <= 0:
        raise ValueError("interval must be positive")

    buffer = SightingBuffer(min_rssi=min_rssi)
    scanner = await open_scanner(buffer)
    try:
        while True:
            await asyncio.sleep(interval)
            at = datetime.now(timezone.utc)
            devices = buffer.drain(at)
            floor = predict_floor()
            yield ScanUpdate(at=at, floor=floor, devices=devices)
    finally:
        await scanner.stop()


def _devices_payload(devices: list[Sighting]) -> list[dict[str, Any]]:
    return [
        {
            "address": d.address,
            "rssi": d.rssi,
            "seenAt": d.seen_at.isoformat().replace("+00:00", "Z"),
        }
        for d in devices
    ]


def render_text(update: ScanUpdate) -> str:
    floor = update.floor
    if floor is None:
        floor_line = "floor=offline"
    elif floor.get("known"):
        floor_line = (
            f"floor={floor.get('floor')}  "
            f"bssid={floor.get('bssid')}  "
            f"confidence={floor.get('confidence') or '-'}"
        )
    else:
        floor_line = f"floor=unknown  bssid={floor.get('bssid')}"

    lines = [
        f"at={update.at.isoformat().replace('+00:00', 'Z')}",
        floor_line,
        f"devices={update.device_count}  min_rssi={RSSI_FLOOR}",
        f"{'RSSI':>6}  ADDRESS",
        f"{'----':>6}  -------",
    ]
    if not update.devices:
        lines.append("(none)")
    else:
        for d in update.devices:
            lines.append(f"{d.rssi:>6}  {d.address}")
    return "\n".join(lines)


def render_json(update: ScanUpdate) -> str:
    floor = update.floor
    payload = {
        "at": update.at.isoformat().replace("+00:00", "Z"),
        "floor": floor,
        "deviceCount": update.device_count,
        "devices": _devices_payload(update.devices),
    }
    return json.dumps(payload, indent=2)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description=(
            "Periodically predict Trottier floor and list nearby BLE devices "
            f"(RSSI ≥ {RSSI_FLOOR} dBm). Ctrl-C to stop."
        ),
    )
    p.add_argument(
        "--interval",
        type=float,
        default=BATCH_INTERVAL,
        help=f"Seconds between updates (default: {BATCH_INTERVAL:g})",
    )
    p.add_argument(
        "--min-rssi",
        type=int,
        default=RSSI_FLOOR,
        help=f"Minimum RSSI in dBm (default: {RSSI_FLOOR})",
    )
    p.add_argument(
        "--json",
        action="store_true",
        help="Emit JSON instead of a text table",
    )
    p.add_argument(
        "--once",
        action="store_true",
        help="Emit a single update then exit",
    )
    return p.parse_args(argv)


async def _amain(args: argparse.Namespace) -> int:
    stop = asyncio.Event()

    def _request_stop(*_sig: object) -> None:
        stop.set()

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, _request_stop)
        except NotImplementedError:
            pass

    try:
        agen = run(interval=args.interval, min_rssi=args.min_rssi)
        async for update in agen:
            body = render_json(update) if args.json else render_text(update)
            if not args.json and sys.stdout.isatty() and not args.once:
                sys.stdout.write("\033[2J\033[H")
            print(body, flush=True)
            if args.once or stop.is_set():
                break
        await agen.aclose()
    except Exception as exc:  # noqa: BLE001 — surface adapter errors cleanly
        print(f"scan-loop failed: {exc}", file=sys.stderr)
        return 1
    return 0


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    if args.interval <= 0:
        print("--interval must be positive", file=sys.stderr)
        return 2
    return asyncio.run(_amain(args))


if __name__ == "__main__":
    raise SystemExit(main())
