"""Unit tests for map-aps ranking and CSV dedupe (no live Wi-Fi)."""

from __future__ import annotations

import threading
from pathlib import Path

from scanner.map_ap import (
    MappingRow,
    PollOptions,
    ScanTally,
    _validate_building,
    dedupe_rows,
    flush_mappings,
    load_csv,
    next_id,
    poll_until,
)


def _ap(bssid: str, rssi: int, ssid: str = "wpa.mcgill.ca") -> dict:
    return {"bssid": bssid, "ssid": ssid, "rssi": rssi}


def test_tally_groups_sibling_ssids_per_radio() -> None:
    tally = ScanTally()
    tally.add_scan(
        [
            _ap("44:48:c1:a4:39:50", -57),
            _ap("44:48:c1:a4:39:51", -58, "eduroam"),
            _ap("44:48:c1:a4:1a:40", -61),
        ]
    )
    top = tally.ranked()[0]
    assert top.label == "44:48:c1:a4:39:5x"
    assert top.bssid == "44:48:c1:a4:39:50"
    assert top.ssids == {"wpa.mcgill.ca", "eduroam"}
    assert top.rssi_max == -57


def test_tally_no_group_keeps_bssids_separate() -> None:
    tally = ScanTally(group_radios=False)
    tally.add_scan([_ap("44:48:c1:a4:39:50", -57), _ap("44:48:c1:a4:39:51", -58)])
    assert [c.bssid for c in tally.ranked()] == ["44:48:c1:a4:39:50", "44:48:c1:a4:39:51"]


def test_tally_ignores_repeated_cached_scan() -> None:
    tally = ScanTally()
    scan = [_ap("aa:aa:aa:aa:aa:10", -50)]
    assert tally.add_scan(scan) is not None
    assert tally.add_scan(list(scan)) is None
    assert tally.add_scan([]) is None
    assert (tally.fresh, tally.polls) == (1, 3)
    assert tally.ranked()[0].wins == 1


def test_tally_ranks_by_wins_then_mean_rssi() -> None:
    tally = ScanTally()
    a, b, c = "aa:aa:aa:aa:aa:10", "bb:bb:bb:bb:bb:10", "cc:cc:cc:cc:cc:10"
    tally.add_scan([_ap(a, -50), _ap(b, -60), _ap(c, -70)])
    tally.add_scan([_ap(a, -55), _ap(b, -52), _ap(c, -71)])
    tally.add_scan([_ap(a, -51), _ap(b, -60), _ap(c, -65)])
    ranked = tally.ranked()
    assert [r.bssid for r in ranked] == [a, b, c]
    assert (ranked[0].wins, ranked[1].wins, ranked[2].wins) == (2, 1, 0)
    assert ranked[1].seen == 3


def test_poll_until_stops_after_max_fresh_scans() -> None:
    scans = iter(
        [
            [_ap("aa:aa:aa:aa:aa:10", -50)],
            [_ap("aa:aa:aa:aa:aa:10", -50)],  # cached repeat, not counted
            [_ap("aa:aa:aa:aa:aa:10", -51)],
        ]
    )
    stop = threading.Event()
    tally = poll_until(
        stop=stop,
        opts=PollOptions(interval=0.001, max_scans=2),
        scan=lambda: next(scans),
    )
    assert stop.is_set()
    assert (tally.fresh, tally.polls) == (2, 3)


def test_next_id() -> None:
    assert next_id("12") == "13"
    assert next_id("B3") == "B4"
    assert next_id("09") == "10"
    assert next_id("lobby") is None


def test_validate_building_accepts_unique_prefix() -> None:
    assert _validate_building("Trot") == "trottier"
    assert _validate_building("islamic studies") == "islamic_studies"


def test_dedupe_keeps_most_recent_per_id_floor() -> None:
    rows = [
        MappingRow("1", 2, "aa:aa:aa:aa:aa:01"),
        MappingRow("1", 2, "bb:bb:bb:bb:bb:02"),
        MappingRow("2", 2, "cc:cc:cc:cc:cc:03"),
        MappingRow("1", 3, "dd:dd:dd:dd:dd:04"),
    ]
    out = dedupe_rows(rows)
    assert out == [
        MappingRow("1", 2, "bb:bb:bb:bb:bb:02"),
        MappingRow("2", 2, "cc:cc:cc:cc:cc:03"),
        MappingRow("1", 3, "dd:dd:dd:dd:dd:04"),
    ]


def test_dedupe_sorts_ids_numerically() -> None:
    rows = [MappingRow("10", 0, "a"), MappingRow("9", 0, "b"), MappingRow("B", 0, "c")]
    assert [r.ap_id for r in dedupe_rows(rows)] == ["9", "10", "B"]


def test_flush_merges_file_with_session(tmp_path: Path) -> None:
    path = tmp_path / "ap_bssid_trottier.csv"
    path.write_text(
        "ID,floor_number,bssid\n"
        "1,0,aa:aa:aa:aa:aa:01\n"
        "2,0,bb:bb:bb:bb:bb:02\n",
        encoding="utf-8",
    )
    session = [
        MappingRow("1", 0, "cc:cc:cc:cc:cc:03"),  # replaces id 1
        MappingRow("3", 1, "dd:dd:dd:dd:dd:04"),
    ]
    merged = flush_mappings(path, session)
    assert merged == [
        MappingRow("1", 0, "cc:cc:cc:cc:cc:03"),
        MappingRow("2", 0, "bb:bb:bb:bb:bb:02"),
        MappingRow("3", 1, "dd:dd:dd:dd:dd:04"),
    ]
    assert load_csv(path) == merged
