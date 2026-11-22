"""Unit tests for the batch body and AP normalization (no network / radio)."""

from __future__ import annotations

from datetime import datetime, timezone
from uuid import UUID

from scanner.poster import build_batch_body
from scanner.scan_loop import MAX_APS, normalize_aps

_ID = UUID("00000000-0000-0000-0000-000000000001")
_AT = datetime(2026, 11, 22, 12, 0, tzinfo=timezone.utc)


def test_body_without_aps_sends_empty_list() -> None:
    body = build_batch_body(
        scanner_id=_ID, bssid="44:48:c1:a4:1a:50", sent_at=_AT, sightings=[-67, -72]
    )
    assert body == {
        "scannerId": str(_ID),
        "bssid": "44:48:c1:a4:1a:50",
        "sentAt": "2026-11-22T12:00:00Z",
        "sightings": [-67, -72],
        "aps": [],
    }


def test_body_with_aps() -> None:
    aps = [{"bssid": "44:48:c1:a4:1a:52", "rssi": -48}]
    body = build_batch_body(
        scanner_id=_ID, bssid="44:48:c1:a4:1a:50", sent_at=_AT, sightings=[], aps=aps
    )
    assert body["aps"] == aps
    assert body["sightings"] == []


def test_normalize_aps_lowercases_dedupes_and_sorts() -> None:
    raw = [
        {"bssid": "44:48:C1:A4:1A:52", "ssid": "x", "rssi": -60},
        {"bssid": "44:48:c1:a4:1a:52", "ssid": "y", "rssi": -50},
        {"bssid": "44:48:c1:a4:39:50", "ssid": "z", "rssi": -70},
    ]
    assert normalize_aps(raw) == [
        {"bssid": "44:48:c1:a4:1a:52", "rssi": -50},
        {"bssid": "44:48:c1:a4:39:50", "rssi": -70},
    ]


def test_normalize_aps_clamps_and_drops_bad_entries() -> None:
    raw = [
        {"bssid": "44:48:c1:a4:1a:50", "rssi": 5},
        {"bssid": "44:48:c1:a4:1a:51", "rssi": -200},
        {"bssid": "not-a-mac", "rssi": -40},
        {"bssid": None, "rssi": -40},
        {"bssid": "44:48:c1:a4:1a:53"},
        {"bssid": "44:48:c1:a4:1a:54", "rssi": "junk"},
    ]
    assert normalize_aps(raw) == [
        {"bssid": "44:48:c1:a4:1a:50", "rssi": 0},
        {"bssid": "44:48:c1:a4:1a:51", "rssi": -127},
    ]


def test_normalize_aps_caps_strongest() -> None:
    raw = [{"bssid": f"00:00:00:00:01:{i:02x}", "rssi": -i} for i in range(100)]
    out = normalize_aps(raw)
    assert len(out) == MAX_APS
    assert out[0] == {"bssid": "00:00:00:00:01:00", "rssi": 0}
    assert out[-1]["rssi"] == -(MAX_APS - 1)
