"""Main scanner loop: periodic floor prediction + BLE sightings + batch POST."""

from __future__ import annotations

import argparse
import asyncio
import json
import logging
import signal
import sys
from collections.abc import AsyncIterator
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

import httpx

from .ble import RSSI_FLOOR, Sighting, SightingBuffer, open_scanner
from .config import ScannerConfig, load_config
from .poster import send_batch
from .predict import predict_floor

BATCH_INTERVAL = 15.0

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class ScanUpdate:
    """One interval's observation: predicted floor + BLE devices."""

    at: datetime
    floor: dict[str, Any] | None
    devices: list[Sighting]

    @property
    def device_count(self) -> int:
        return len(self.devices)

    @property
    def sightings(self) -> list[int]:
        """RSSI list for ``BatchRequest.sightings`` (FX-01.2)."""
        return [d.rssi for d in self.devices]

    @property
    def bssid(self) -> str | None:
        if self.floor is None:
            return None
        bssid = self.floor.get("bssid")
        return bssid if isinstance(bssid, str) and bssid else None


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


async def post_update(
    update: ScanUpdate,
    config: ScannerConfig,
    client: httpx.AsyncClient,
) -> bool:
    """POST ``update`` if online; skip when offline. Returns whether a POST succeeded."""
    bssid = update.bssid
    if bssid is None:
        log.info("skip POST (offline / no bssid)")
        return False
    return await send_batch(
        base_url=config.server_url,
        api_key=config.api_key,
        scanner_id=config.scanner_id,
        bssid=bssid,
        sent_at=update.at,
        sightings=update.sightings,
        client=client,
    )


def render_text(update: ScanUpdate, *, posted: bool | None = None) -> str:
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

    post_line = ""
    if posted is True:
        post_line = "posted=ok"
    elif posted is False and update.bssid is None:
        post_line = "posted=skipped"
    elif posted is False:
        post_line = "posted=failed"
    elif posted is None:
        post_line = "posted=disabled"

    lines = [
        f"at={update.at.isoformat().replace('+00:00', 'Z')}",
        floor_line,
        f"devices={update.device_count}  min_rssi={RSSI_FLOOR}  {post_line}".rstrip(),
        f"{'RSSI':>6}  ADDRESS",
        f"{'----':>6}  -------",
    ]
    if not update.devices:
        lines.append("(none)")
    else:
        for d in update.devices:
            lines.append(f"{d.rssi:>6}  {d.address}")
    return "\n".join(lines)


def render_json(update: ScanUpdate, *, posted: bool | None = None) -> str:
    payload = {
        "at": update.at.isoformat().replace("+00:00", "Z"),
        "floor": update.floor,
        "deviceCount": update.device_count,
        "sightings": update.sightings,
        "devices": [
            {"address": d.address, "rssi": d.rssi} for d in update.devices
        ],
        "posted": posted,
    }
    return json.dumps(payload, indent=2)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description=(
            "Periodically predict Trottier floor, list nearby BLE devices "
            f"(RSSI ≥ {RSSI_FLOOR} dBm), and POST RSSI batches to the server. "
            "Ctrl-C to stop."
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
    p.add_argument(
        "--no-post",
        action="store_true",
        help="Do not POST batches (local scan + floor only)",
    )
    p.add_argument(
        "--server-url",
        default=None,
        help="Override LARP_SERVER_URL",
    )
    p.add_argument(
        "--api-key",
        default=None,
        help="Override LARP_API_KEY",
    )
    return p.parse_args(argv)


async def _amain(args: argparse.Namespace) -> int:
    logging.basicConfig(
        level=logging.INFO,
        format="%(levelname)s %(name)s: %(message)s",
        stream=sys.stderr,
    )

    stop = asyncio.Event()

    def _request_stop(*_sig: object) -> None:
        stop.set()

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, _request_stop)
        except NotImplementedError:
            pass

    config: ScannerConfig | None = None
    if not args.no_post:
        try:
            config = load_config(server_url=args.server_url, api_key=args.api_key)
        except ValueError as exc:
            print(f"scan-loop config: {exc}", file=sys.stderr)
            print("Set LARP_SERVER_URL and LARP_API_KEY, or pass --no-post.", file=sys.stderr)
            return 2
        log.info(
            "posting to %s/api/batches scanner_id=%s id_file=%s",
            config.server_url,
            config.scanner_id,
            config.scanner_id_path,
        )

    try:
        agen = run(interval=args.interval, min_rssi=args.min_rssi)
        async with httpx.AsyncClient(timeout=10.0) as client:
            async for update in agen:
                posted: bool | None = None
                if config is not None:
                    posted = await post_update(update, config, client)

                body = (
                    render_json(update, posted=posted)
                    if args.json
                    else render_text(update, posted=posted)
                )
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
