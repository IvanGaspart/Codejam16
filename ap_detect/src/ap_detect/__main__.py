import logging
import os

from .core import get_current_ap

logging.basicConfig(level=logging.DEBUG if os.environ.get("IW_DEBUG") else logging.WARNING)
print(get_current_ap())
