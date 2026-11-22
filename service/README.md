# Scanner service

The Python service runs on a laptop connected to Wi-Fi. Every 15 seconds it sends the server one Bluetooth signal strength per distinct device, the connected Wi-Fi access point, and nearby access-point readings. The server uses these to estimate floor occupancy and scanner position. Bluetooth device identifiers stay on the laptop.

## Install and run

From the repository root, enter `service/`. You need uv, Python 3.13 or later, and working Wi-Fi and Bluetooth. On Linux, enable BlueZ and install `iw` for nearby Wi-Fi scans.

```sh
cd service
```

On macOS 14 or later, install the Xcode Command Line Tools and build the Wi-Fi helper **before** syncing dependencies so uv packages the built app. For an iCloud-synced checkout, first set `UV_PROJECT_ENVIRONMENT="$HOME/.venvs/larp-scan"` with `export`.

```sh
# macOS 14+ only
bash ../ap_detect/helper/build.sh
```

```sh
uv sync --locked
```

On macOS 14 or later, run `uv run python -m ap_detect --authorize` and allow the WifiInfo Location Services prompt. Run `uv run predict-floor` to check the connected access point and floor.

Start the [server](../server/README.md), then run the scanner. Use the server’s reachable URL if it runs on another machine, and match its API key.

```sh
export LARP_SERVER_URL=http://localhost:8080
export LARP_API_KEY=dev-local-key
uv run scan-loop
```

Use `uv run scan-loop --no-post` to inspect readings locally, `uv run map-aps --help` to survey access points, or `uv run pytest` to run the tests.

## Run at login

From `service/`, run `bash install.sh` as your regular user. It installs a systemd user service on Linux or a LaunchAgent on macOS. Edit the generated `~/.config/larp-scan/env` to set the server URL and key. After editing, restart with `systemctl --user restart larp-scan.service` on Linux or `launchctl kickstart -k gui/$(id -u)/com.codejam16.larp-scan` on macOS. Re-run the installer after updating the scanner.
