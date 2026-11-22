# ap-detect

Get the **BSSID** (and SSID, when available) of the Wi-Fi access point you are connected to, from Python, on macOS, Linux and Windows.

```python
>>> from ap_detect import get_current_ap
>>> get_current_ap()
{'bssid': '44:48:c1:a4:1a:50', 'ssid': 'MyNetwork'}
```

`get_current_ap()` returns `None` if nothing could be read. `ssid` can be `None` even when `bssid` is found.

```bash
uv run python -m ap_detect              # prints the same dict
IW_DEBUG=1 uv run python -m ap_detect   # shows which backend answered and why others failed
```

## Install

The package is not published yet, so install from source:

```bash
git clone <your-repo-url> ap-detect
cd ap-detect
uv sync
```

On macOS this also installs `pyobjc-framework-CoreWLAN`.

### macOS: build the helper (once)

On macOS 14 and later, a small helper app is needed to read the BSSID (see [Why macOS is different](#why-macos-is-different)). The built app is not committed to git, so build it after cloning. You need the Xcode Command Line Tools:

```bash
xcode-select --install     # skip if already installed
bash helper/build.sh       # creates src/ap_detect/_bin/WifiInfo.app
uv run python -m ap_detect --authorize
```

`--authorize` shows the one-time macOS Location Services prompt for the helper. Allow it, and it prints `granted`. After that, `get_current_ap()` works silently.

Linux and Windows need no build step.

## Usage

```python
from ap_detect import authorize, get_current_ap

authorize()          # macOS only: triggers the Location prompt once, see return values below
ap = get_current_ap()
if ap:
    print(ap["bssid"], ap["ssid"])
```

`authorize()` returns one of:

| Value | Meaning |
|-------|---------|
| `granted` | The helper has permission and returned Wi-Fi data |
| `denied` | Location Services was refused for "WifiInfo" |
| `timeout` | The prompt was not answered within 60 seconds |
| `unclear` | The helper ran but returned no Wi-Fi data (not connected to Wi-Fi, or still gated) |
| `helper-missing` | The helper app was not found, or could not be launched or read (usually: not built yet) |
| `not-needed` | Not macOS |

## How it works

| OS | Backends, in order |
|----|--------------------|
| Linux | `iw`, then `nmcli` |
| Windows | `netsh wlan show interfaces` (output is localized; Windows 11 24H2+ may require Location access) |
| macOS 14+ | CoreWLAN, then the **WifiInfo.app** helper |
| macOS < 14 | CoreWLAN, then `system_profiler` |

### Why macOS is different

Since macOS 14, the BSSID and SSID are only given to apps that Location Services trusts. A plain Python process has no such trust, and every command-line source (`system_profiler`, `ipconfig`, `wdutil`, `ioreg`, `airport`) returns `<redacted>`. Pure Python cannot get around this, so the package offers two routes:

1. **CoreWLAN directly.** Works if the app running Python (Terminal, iTerm2, your IDE) has been granted Location Services in *System Settings → Privacy & Security → Location Services*. Apps only appear in that list after they have requested access, so your terminal may not be listed.
2. **WifiInfo.app (recommended).** A tiny background app with its own Location Services entry. You approve it once ("WifiInfo") and every later call is silent. It is launched with `open`, not executed directly, so macOS attributes the permission to the helper and not to your terminal.

## Configuration

| Variable | Effect |
|----------|--------|
| `IW_DEBUG=1` | Log which backend ran and why others failed (CLI only) |
| `IW_HELPER=/path/WifiInfo.app` | Use a helper app from a custom location |

## Building the helper in detail

The helper source is in [`helper/`](helper/). `build.sh` targets macOS 14+, builds a universal binary when the toolchain allows it (otherwise arm64 only), and signs it ad-hoc. To sign with your own identity:

```bash
CODESIGN_IDENTITY="Developer ID Application: Your Name (TEAMID)" bash helper/build.sh
```

Check the result:

```bash
ls -d src/ap_detect/_bin/WifiInfo.app
lipo -archs src/ap_detect/_bin/WifiInfo.app/Contents/MacOS/WifiInfo
open -W -n -a src/ap_detect/_bin/WifiInfo.app --args --out /tmp/wifi.json && cat /tmp/wifi.json
```

## Troubleshooting

- **`helper-missing`.** The helper has not been built. Run `bash helper/build.sh`, then `uv run python -m ap_detect --authorize` again.
- **`denied`.** Enable "WifiInfo" in *System Settings → Privacy & Security → Location Services*, then run `--authorize` again.
- **`None` on macOS.** Run with `IW_DEBUG=1` to see which backend failed, and make sure you are connected to Wi-Fi.
- **No permission prompt appears.** Confirm the helper is built and found (`IW_HELPER` if it is somewhere else), then run `--authorize`.
- **Build fails linking `x86_64`.** The Command Line Tools may lack Intel Swift libraries. `build.sh` then skips that slice and builds arm64 only, which is fine on Apple Silicon. Details are in `.build/err-x86_64.log`.
- **`codesign` complains about "detritus".** The folder is probably synced by iCloud. Run `xattr -cr WifiInfo.app` and sign again, or build outside the synced folder.
- **Linux: `None`.** Install `iw` or NetworkManager (`nmcli`), and make sure you are connected over Wi-Fi.

## Limitations

- The helper is ad-hoc signed by default. If Gatekeeper complains on other machines, sign with a Developer ID certificate and notarize.
- Windows and Linux backends parse tool output and have had little testing.
- The helper targets macOS 14 and later; older macOS versions do not need it.
- Not on PyPI yet. A published wheel would need to bundle the built helper.

## License

TBD (pick one, for example MIT, and add a `LICENSE` file).
