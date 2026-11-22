import argparse
import logging
import os

from .core import authorize, get_current_ap

parser = argparse.ArgumentParser(prog="ap_detect")
parser.add_argument("--authorize", action="store_true",
                    help="macOS: show the Location Services prompt for the helper app")
args = parser.parse_args()

logging.basicConfig(level=logging.DEBUG if os.environ.get("IW_DEBUG") else logging.WARNING)
print(authorize() if args.authorize else get_current_ap())
