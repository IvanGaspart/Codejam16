"""Get the BSSID / SSID of the currently connected Wi-Fi access point,
and scan nearby APs with RSSI (macOS + Linux).

    >>> from ap_detect import get_current_ap, scan_nearby_aps
    >>> get_current_ap()
    {'bssid': '44:48:c1:a4:1a:50', 'ssid': 'MyNetwork'}   # ssid may be None
    >>> scan_nearby_aps()
    [{'bssid': '…', 'ssid': 'wpa.mcgill.ca', 'rssi': -52}, ...]

get_current_ap() returns None if nothing could be read. Set IW_DEBUG=1 to see why.

macOS note
----------
Since macOS 14, the BSSID/SSID are only returned to processes that Location
Services trusts. Backends are tried in this order:

1. CoreWLAN (needs `pyobjc-framework-CoreWLAN` + Location Services for the
   app that runs Python, e.g. iTerm2).
2. WifiInfo.app -- a small bundled helper (build it with helper/build.sh) that
   has its own Location Services entry. One permission prompt on first use,
   then silent. Path: next to this file, or $IW_HELPER.
3. `system_profiler` (only works on macOS < 14).

Nearby scans use the same Location Services gate on macOS (helper --scan or
CoreWLAN). On Linux they use ``iw`` scan dump (with an optional nmcli rescan).
"""
from __future__ import annotations

import json
import logging
import os
import platform
import re
import shutil
import subprocess
import tempfile
import time
from pathlib import Path
from typing import TypedDict

log = logging.getLogger(__name__)

_MAC_RE = re.compile(r"(?<![0-9a-f:])([0-9a-f]{1,2}(?::[0-9a-f]{1,2}){5})(?![0-9a-f:])", re.I)


class AP(TypedDict):
    bssid: str
    ssid: str | None


class ScannedAP(TypedDict):
    bssid: str
    ssid: str | None
    rssi: int


# --------------------------------------------------------------------------- #
# helpers
# --------------------------------------------------------------------------- #
def _norm_mac(raw: str | None) -> str | None:
    """Lowercase, zero-pad each octet, reject redacted / all-zero values."""
    if not raw:
        return None
    m = _MAC_RE.search(raw)
    if not m:
        return None
    mac = ":".join(p.zfill(2) for p in m.group(1).lower().split(":"))
    return None if mac == "00:00:00:00:00:00" else mac


def _run(cmd: list[str], timeout: float = 10) -> str | None:
    """Run a command, returning stdout or None on any failure."""
    try:
        return subprocess.check_output(
            cmd, text=True, stderr=subprocess.DEVNULL, timeout=timeout
        )
    except (OSError, subprocess.SubprocessError) as exc:
        log.debug("%s failed: %r", cmd[0], exc)
        return None


def _ap(bssid: str | None, ssid: str | None = None) -> AP | None:
    mac = _norm_mac(bssid)
    if not mac:
        return None
    ssid = ssid.strip() if ssid else None
    if ssid and "redacted" in ssid.lower():
        ssid = None
    return {"bssid": mac, "ssid": ssid or None}


# --------------------------------------------------------------------------- #
# Linux
# --------------------------------------------------------------------------- #
def _linux() -> AP | None:
    out = _run(["iw", "dev"])
    if out:
        for iface in re.findall(r"Interface\s+(\S+)", out):
            link = _run(["iw", "dev", iface, "link"])
            if not link:
                continue
            bssid = re.search(r"Connected to\s+(\S+)", link)
            ssid = re.search(r"SSID:\s+(.+)", link)
            ap = _ap(bssid.group(1) if bssid else None, ssid.group(1) if ssid else None)
            if ap:
                return ap

    # Fallback: NetworkManager. Output looks like  yes:SSID:AA\:BB\:CC\:DD\:EE\:FF
    out = _run(["nmcli", "-t", "-f", "active,ssid,bssid", "dev", "wifi"])
    if out:
        for line in out.splitlines():
            m = re.match(r"^yes:(.*):((?:[0-9A-Fa-f]{2}\\:){5}[0-9A-Fa-f]{2})$", line)
            if m:
                return _ap(m.group(2).replace("\\:", ":"), m.group(1).replace("\\:", ":"))
    return None


# --------------------------------------------------------------------------- #
# macOS
# --------------------------------------------------------------------------- #
def _darwin_corewlan() -> AP | None:
    try:
        import CoreWLAN  # type: ignore  # pip install pyobjc-framework-CoreWLAN
    except ImportError:
        log.debug("pyobjc-framework-CoreWLAN not installed")
        return None
    iface = CoreWLAN.CWWiFiClient.sharedWiFiClient().interface()
    if iface is None:
        return None
    return _ap(iface.bssid(), iface.ssid())  # both None without Location Services


def _helper_path() -> Path | None:
    """Locate WifiInfo.app: $IW_HELPER, else next to this file."""
    candidates = []
    if os.environ.get("IW_HELPER"):
        candidates.append(Path(os.environ["IW_HELPER"]))
    candidates.append(Path(__file__).resolve().parent / "_bin" / "WifiInfo.app")
    return next((p for p in candidates if p.is_dir()), None)


def run_helper(*, scan: bool = False) -> dict | None:
    """Launch WifiInfo.app and return its JSON reply, or None if unavailable.

    It is launched through `open` (not executed directly) so that macOS treats
    it as its own app instead of attributing it to the terminal. The first run
    shows the one-time Location Services prompt.

    When ``scan`` is True, the helper runs a nearby-network scan (``--scan``).
    """
    app = _helper_path()
    if app is None or not shutil.which("open"):
        return None
    with tempfile.TemporaryDirectory() as tmp:
        out = Path(tmp) / "out.json"
        helper_args = ["--out", str(out)]
        if scan:
            helper_args.append("--scan")
        # -W waits for exit, -n forces a fresh instance
        if _run(
            ["open", "-W", "-n", "-a", str(app), "--args", *helper_args],
            timeout=90,
        ) is None:
            return None
        try:
            return json.loads(out.read_text())
        except (OSError, ValueError):
            return None


def authorize() -> str:
    """Trigger the macOS Location Services prompt for the helper (macOS only).

    Returns "granted", "denied", "timeout", "unclear" (permission may be fine
    but no Wi-Fi data came back, e.g. not connected), "helper-missing", or
    "not-needed" on other platforms.
    """
    if platform.system() != "Darwin":
        return "not-needed"
    data = run_helper()
    if data is None:
        return "helper-missing"
    if data.get("bssid"):
        return "granted"
    return {"denied": "denied", "timeout": "timeout"}.get(data.get("error"), "unclear")


def _darwin_helper() -> AP | None:
    data = run_helper()
    if data is None:
        return None
    if "error" in data:
        log.debug("WifiInfo.app reported: %s", data["error"])
        return None
    return _ap(data.get("bssid"), data.get("ssid"))


def _darwin_system_profiler() -> AP | None:
    try:
        major = int(platform.mac_ver()[0].split(".")[0])
    except ValueError:
        major = 0
    if major >= 14:  # redacted from 14 on, don't waste several seconds
        return None
    out = _run(["system_profiler", "SPAirPortDataType"], timeout=30)
    if not out:
        return None
    bssid = re.search(r"BSSID:\s+(\S+)", out, re.I)
    ssid = re.search(r"Current Network Information:\s*\n\s*(.+?):\s*\n", out)
    return _ap(bssid.group(1) if bssid else None, ssid.group(1) if ssid else None)


def _darwin() -> AP | None:
    for backend in (_darwin_corewlan, _darwin_helper, _darwin_system_profiler):
        try:
            ap = backend()
        except Exception as exc:  # a backend must never crash the caller
            log.debug("%s raised %r", backend.__name__, exc)
            continue
        if ap:
            return ap
    log.warning(
        "Could not read the BSSID. On macOS 14+ grant Location Services to your "
        "terminal app (System Settings > Privacy & Security > Location Services) "
        "and `pip install pyobjc-framework-CoreWLAN`, or build WifiInfo.app with "
        "helper/build.sh and run `python -m ap_detect --authorize`."
    )
    return None


# --------------------------------------------------------------------------- #
# Windows
# --------------------------------------------------------------------------- #
def _windows() -> AP | None:
    # Note: output is localized, and Windows 11 24H2+ may require Location access.
    out = _run(["netsh", "wlan", "show", "interfaces"])
    if not out:
        return None
    bssid = re.search(r"^\s*BSSID\s*:\s*(\S+)", out, re.I | re.M)
    ssid = re.search(r"^\s*SSID\s*:\s*(.+)$", out, re.M)
    return _ap(bssid.group(1) if bssid else None, ssid.group(1) if ssid else None)


# --------------------------------------------------------------------------- #
# public API
# --------------------------------------------------------------------------- #
def get_current_ap() -> AP | None:
    """Return {'bssid': ..., 'ssid': ...} for the connected AP, or None."""
    backends = {"Linux": _linux, "Darwin": _darwin, "Windows": _windows}
    fn = backends.get(platform.system())
    return fn() if fn else None


# --------------------------------------------------------------------------- #
# Nearby AP scan (macOS + Linux)
# --------------------------------------------------------------------------- #
def _scanned(bssid: str | None, ssid: str | None, rssi: float | int | None) -> ScannedAP | None:
    mac = _norm_mac(bssid)
    if not mac or rssi is None:
        return None
    try:
        rssi_i = int(round(float(rssi)))
    except (TypeError, ValueError):
        return None
    clean_ssid = ssid.strip() if ssid else None
    if clean_ssid and "redacted" in clean_ssid.lower():
        clean_ssid = None
    return {"bssid": mac, "ssid": clean_ssid, "rssi": rssi_i}


def _dedupe_strongest(rows: list[ScannedAP]) -> list[ScannedAP]:
    best: dict[str, ScannedAP] = {}
    for row in rows:
        prev = best.get(row["bssid"])
        if prev is None or row["rssi"] > prev["rssi"]:
            best[row["bssid"]] = row
    return sorted(best.values(), key=lambda r: (-r["rssi"], r["bssid"]))


def _linux_scan() -> list[ScannedAP]:
    out = _run(["iw", "dev"])
    if not out:
        return []
    iface_m = re.search(r"Interface\s+(\S+)", out)
    if not iface_m:
        return []
    iface = iface_m.group(1)
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
    time.sleep(2.0)
    dump = _run(["iw", "dev", iface, "scan", "dump"], timeout=30)
    if not dump:
        return []

    rows: list[ScannedAP] = []
    cur_bssid: str | None = None
    cur_ssid: str | None = None
    cur_rssi: float | None = None

    def flush() -> None:
        nonlocal cur_bssid, cur_ssid, cur_rssi
        ap = _scanned(cur_bssid, cur_ssid, cur_rssi)
        if ap:
            rows.append(ap)
        cur_bssid, cur_ssid, cur_rssi = None, None, None

    for line in dump.splitlines():
        if line.startswith("BSS "):
            flush()
            m = re.match(r"BSS ([0-9a-f:]{17})", line, re.I)
            cur_bssid = m.group(1) if m else None
        elif "signal:" in line and cur_rssi is None:
            m = re.search(r"signal:\s*([-0-9.]+)", line)
            if m:
                cur_rssi = float(m.group(1))
        elif "SSID:" in line:
            ssid = line.split("SSID:", 1)[1].strip()
            if ssid or cur_ssid is None:
                cur_ssid = ssid
    flush()
    return _dedupe_strongest(rows)


# Set once an in-process CoreWLAN scan comes back with networks but no BSSIDs
# (this process lacks Location Services). Retrying costs several seconds per
# scan for nothing, so later scans go straight to the helper.
_corewlan_scan_gated = False


def _darwin_scan_corewlan() -> list[ScannedAP]:
    global _corewlan_scan_gated
    if _corewlan_scan_gated:
        return []
    try:
        import CoreWLAN  # type: ignore
    except ImportError:
        log.debug("pyobjc-framework-CoreWLAN not installed")
        _corewlan_scan_gated = True
        return []
    iface = CoreWLAN.CWWiFiClient.sharedWiFiClient().interface()
    if iface is None:
        return []
    # scanForNetworksWithName_error_(None) → all networks; BSSID needs Location.
    networks, err = iface.scanForNetworksWithName_error_(None, None)
    if err is not None:
        log.debug("CoreWLAN scan error: %r", err)
    if not networks:
        return []
    rows: list[ScannedAP] = []
    for net in networks:
        ap = _scanned(net.bssid(), net.ssid(), net.rssiValue())
        if ap:
            rows.append(ap)
    if not rows:
        log.debug("CoreWLAN scan returned no BSSIDs (Location gate); using helper from now on")
        _corewlan_scan_gated = True
    return _dedupe_strongest(rows)


def _darwin_scan_helper() -> list[ScannedAP]:
    data = run_helper(scan=True)
    if data is None:
        return []
    if "error" in data:
        log.debug("WifiInfo.app scan reported: %s", data["error"])
        return []
    rows: list[ScannedAP] = []
    for item in data.get("networks") or []:
        if not isinstance(item, dict):
            continue
        ap = _scanned(item.get("bssid"), item.get("ssid"), item.get("rssi"))
        if ap:
            rows.append(ap)
    return _dedupe_strongest(rows)


def _darwin_scan() -> list[ScannedAP]:
    for backend in (_darwin_scan_corewlan, _darwin_scan_helper):
        try:
            rows = backend()
        except Exception as exc:  # a backend must never crash the caller
            log.debug("%s raised %r", backend.__name__, exc)
            continue
        if rows:
            return rows
    log.warning(
        "Could not scan nearby APs. On macOS 14+ grant Location Services and "
        "rebuild WifiInfo.app (helper/build.sh), then run "
        "`python -m ap_detect --authorize`."
    )
    return []


def scan_nearby_aps() -> list[ScannedAP]:
    """Return nearby APs as ``{bssid, ssid, rssi}``, strongest-first per BSSID.

    Empty list if the platform is unsupported or the scan failed. Windows is
    not implemented yet.
    """
    system = platform.system()
    if system == "Linux":
        return _linux_scan()
    if system == "Darwin":
        return _darwin_scan()
    log.debug("scan_nearby_aps: unsupported platform %s", system)
    return []


if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG if os.environ.get("IW_DEBUG") else logging.WARNING)
    print(get_current_ap())
