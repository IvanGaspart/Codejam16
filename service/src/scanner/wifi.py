"""Get the BSSID / SSID of the currently connected Wi-Fi access point.

    >>> from scanner.wifi import get_current_ap
    >>> get_current_ap()
    {'bssid': '44:48:c1:a4:1a:50', 'ssid': 'MyNetwork'}   # ssid may be None

Returns None if nothing could be read. Set IW_DEBUG=1 to see why.

Vendored from the standalone ap_detect package so scanner is self-contained.

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
from pathlib import Path
from typing import TypedDict

log = logging.getLogger(__name__)

_MAC_RE = re.compile(r"(?<![0-9a-f:])([0-9a-f]{1,2}(?::[0-9a-f]{1,2}){5})(?![0-9a-f:])", re.I)


class AP(TypedDict):
    bssid: str
    ssid: str | None


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


def run_helper() -> dict | None:
    """Launch WifiInfo.app and return its JSON reply, or None if unavailable.

    It is launched through `open` (not executed directly) so that macOS treats
    it as its own app instead of attributing it to the terminal. The first run
    shows the one-time Location Services prompt.
    """
    app = _helper_path()
    if app is None or not shutil.which("open"):
        return None
    with tempfile.TemporaryDirectory() as tmp:
        out = Path(tmp) / "out.json"
        # -W waits for exit, -n forces a fresh instance
        cmd = ["open", "-W", "-n", "-a", str(app), "--args", "--out", str(out)]
        try:
            proc = subprocess.run(cmd, capture_output=True, text=True, timeout=90)
        except (OSError, subprocess.SubprocessError) as exc:
            log.warning("WifiInfo.app launch failed: %r", exc)
            return None
        if proc.returncode != 0:
            # surface LaunchServices' reason (e.g. -10669 bad CPU type, -10661 not executable)
            log.warning("WifiInfo.app launch failed (open exit %d): %s", proc.returncode, proc.stderr.strip())
            return None
        try:
            return json.loads(out.read_text())
        except (OSError, ValueError):
            return None


def authorize() -> str:
    """Trigger the macOS Location Services prompt for the helper (macOS only).

    Returns "granted", "denied", "timeout", "unclear" (permission may be fine
    but no Wi-Fi data came back, e.g. not connected), "helper-missing",
    "helper-failed" (app present but `open` failed or timed out), or
    "not-needed" on other platforms.
    """
    if platform.system() != "Darwin":
        return "not-needed"
    if _helper_path() is None:
        return "helper-missing"
    data = run_helper()
    if data is None:
        return "helper-failed"
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
        "helper/build.sh and run authorize()."
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


if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG if os.environ.get("IW_DEBUG") else logging.WARNING)
    print(get_current_ap())
