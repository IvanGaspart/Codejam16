"""Scanner runtime config: server URL, API key, and persistent scanner ID."""

from __future__ import annotations

import os
import uuid
from dataclasses import dataclass
from pathlib import Path

DEFAULT_SCANNER_ID_PATH = Path.home() / ".config" / "larp-scan" / "scanner-id"


@dataclass(frozen=True)
class ScannerConfig:
    """Values needed to POST batches to the LARP-scan server."""

    server_url: str
    api_key: str
    scanner_id: uuid.UUID
    scanner_id_path: Path


def load_scanner_id(path: Path | None = None) -> tuple[uuid.UUID, Path]:
    """Load or create a UUID at ``~/.config/larp-scan/scanner-id``."""
    id_path = path or DEFAULT_SCANNER_ID_PATH
    if id_path.is_file():
        raw = id_path.read_text(encoding="utf-8").strip()
        return uuid.UUID(raw), id_path

    scanner_id = uuid.uuid4()
    id_path.parent.mkdir(parents=True, exist_ok=True)
    id_path.write_text(f"{scanner_id}\n", encoding="utf-8")
    return scanner_id, id_path


def load_config(
    *,
    server_url: str | None = None,
    api_key: str | None = None,
    scanner_id_path: Path | None = None,
) -> ScannerConfig:
    """Read ``LARP_SERVER_URL`` / ``LARP_API_KEY`` and the scanner-id file.

    Raises ``ValueError`` if the server URL or API key is missing.
    """
    url = (server_url if server_url is not None else os.environ.get("LARP_SERVER_URL", "")).strip()
    key = (api_key if api_key is not None else os.environ.get("LARP_API_KEY", "")).strip()
    if not url:
        raise ValueError("LARP_SERVER_URL is required (base URL of the LARP-scan server)")
    if not key:
        raise ValueError("LARP_API_KEY is required")

    scanner_id, path = load_scanner_id(scanner_id_path)
    return ScannerConfig(
        server_url=url.rstrip("/"),
        api_key=key,
        scanner_id=scanner_id,
        scanner_id_path=path,
    )
