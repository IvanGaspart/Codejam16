"""Unit tests for map-aps voting and CSV dedupe (no live Wi-Fi)."""

from __future__ import annotations

from pathlib import Path

from scanner.map_ap import (
    MappingRow,
    dedupe_rows,
    flush_mappings,
    load_csv,
    strongest_bssid,
    vote_bssid,
)


def test_strongest_bssid_picks_highest_rssi() -> None:
    aps = [
        {"bssid": "aa:bb:cc:dd:ee:01", "ssid": "a", "rssi": -70},
        {"bssid": "aa:bb:cc:dd:ee:02", "ssid": "b", "rssi": -40},
        {"bssid": "aa:bb:cc:dd:ee:03", "ssid": "c", "rssi": -55},
    ]
    assert strongest_bssid(aps) == "aa:bb:cc:dd:ee:02"


def test_strongest_bssid_empty() -> None:
    assert strongest_bssid([]) is None


def test_vote_bssid_most_common() -> None:
    winners = ["a", "b", "a", "c", "a", "b"]
    assert vote_bssid(winners) == "a"


def test_vote_bssid_tie_prefers_first_seen_among_tied() -> None:
    # b and a both appear twice; a appears first among the tied pair's first wins
    winners = ["b", "a", "b", "a", "c"]
    assert vote_bssid(winners) == "b"


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
