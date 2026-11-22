import argparse
import logging
import os

from .core import authorize, get_current_ap, scan_nearby_aps

parser = argparse.ArgumentParser(prog="ap_detect")
parser.add_argument(
    "--authorize",
    action="store_true",
    help="macOS: show the Location Services prompt for the helper app",
)
parser.add_argument(
    "--scan",
    action="store_true",
    help="Scan nearby APs (bssid, ssid, rssi) instead of the connected AP",
)
args = parser.parse_args()

logging.basicConfig(level=logging.DEBUG if os.environ.get("IW_DEBUG") else logging.WARNING)
if args.authorize:
    print(authorize())
elif args.scan:
    print(scan_nearby_aps())
else:
    print(get_current_ap())
