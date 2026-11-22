"""Continuous BLE advertisement buffer for the scanner loop.

Drops device names (RQ-05) and sightings weaker than ``RSSI_FLOOR``.
Addresses are deduplicated within each interval (strongest RSSI wins).

On Linux/Windows, ``device.address`` is a Bluetooth MAC. On macOS,
CoreBluetooth hides MACs and bleak reports a peripheral UUID instead —
we accept both. Addresses are only used for local dedup; batches send
RSSI values alone (ADR-0002), so macOS and Linux scanners post the same way.
"""

from __future__ import annotations

import re
import threading
from dataclasses import dataclass
from datetime import datetime

from bleak import BleakScanner
from bleak.backends.device import BLEDevice
from bleak.backends.scanner import AdvertisementData

RSSI_FLOOR = -80

_MAC_RE = re.compile(r"^[0-9a-f]{2}(:[0-9a-f]{2}){5}$")
_UUID_RE = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
)


@dataclass(frozen=True)
class Sighting:
    """One BLE address heard during the current interval (no name)."""

    address: str
    rssi: int
    seen_at: datetime


def norm_address(raw: str | None) -> str | None:
    """Normalize a BLE id: lowercase MAC, or lowercase UUID on macOS."""
    if not raw:
        return None
    addr = raw.strip().lower()
    if _UUID_RE.match(addr):
        return addr
    addr = addr.replace("-", ":")
    parts = addr.split(":")
    if len(parts) != 6 or any(not p for p in parts):
        return None
    addr = ":".join(p.zfill(2) for p in parts)
    return addr if _MAC_RE.match(addr) else None


class SightingBuffer:
    """Thread-safe buffer of strongest RSSI per address in the open interval."""

    def __init__(self, *, min_rssi: int = RSSI_FLOOR) -> None:
        self._min_rssi = min_rssi
        self._lock = threading.Lock()
        self._rssi: dict[str, int] = {}

    def on_detect(self, device: BLEDevice, adv: AdvertisementData) -> None:
        address = norm_address(device.address)
        if address is None:
            return
        rssi = int(adv.rssi)
        if rssi < self._min_rssi:
            return
        # Clamp to the server DTO range (−127…0).
        if rssi > 0:
            rssi = 0
        if rssi < -127:
            rssi = -127

        with self._lock:
            prev = self._rssi.get(address)
            if prev is None or rssi > prev:
                self._rssi[address] = rssi

    def drain(self, at: datetime) -> list[Sighting]:
        """Stamp all sightings with ``at``, sort by RSSI, and clear the buffer."""
        with self._lock:
            items = [
                Sighting(address=addr, rssi=rssi, seen_at=at)
                for addr, rssi in self._rssi.items()
            ]
            self._rssi.clear()
        items.sort(key=lambda s: s.rssi, reverse=True)
        return items


async def open_scanner(buffer: SightingBuffer) -> BleakScanner:
    """Start a BleakScanner that feeds ``buffer.on_detect``."""
    scanner = BleakScanner(detection_callback=buffer.on_detect)
    await scanner.start()
    return scanner
