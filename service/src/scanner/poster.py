"""POST FX-01.2 BatchRequest payloads to the LARP-scan server."""

from __future__ import annotations

import logging
from datetime import datetime
from typing import Any
from uuid import UUID

import httpx

log = logging.getLogger(__name__)

API_KEY_HEADER = "X-API-Key"


def _iso_z(at: datetime) -> str:
    return at.isoformat().replace("+00:00", "Z")


def build_batch_body(
    *,
    scanner_id: UUID,
    bssid: str,
    sent_at: datetime,
    sightings: list[int],
    aps: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    """JSON body matching server ``BatchRequest`` (sightings = RSSI ints,
    aps = nearby Wi-Fi ``{bssid, rssi}`` for server-side trilateration)."""
    return {
        "scannerId": str(scanner_id),
        "bssid": bssid,
        "sentAt": _iso_z(sent_at),
        "sightings": list(sightings),
        "aps": list(aps or []),
    }


async def send_batch(
    *,
    base_url: str,
    api_key: str,
    scanner_id: UUID,
    bssid: str,
    sent_at: datetime,
    sightings: list[int],
    aps: list[dict[str, Any]] | None = None,
    client: httpx.AsyncClient | None = None,
    timeout: float = 10.0,
) -> bool:
    """POST one batch to ``{base_url}/api/batches``.

    Returns True on 2xx. On network or HTTP error, logs and returns False
    (SPEC: drop the batch; no retry queue).
    """
    url = f"{base_url.rstrip('/')}/api/batches"
    body = build_batch_body(
        scanner_id=scanner_id,
        bssid=bssid,
        sent_at=sent_at,
        sightings=sightings,
        aps=aps,
    )
    headers = {API_KEY_HEADER: api_key, "Content-Type": "application/json"}

    own_client = client is None
    http = client or httpx.AsyncClient(timeout=timeout)
    try:
        response = await http.post(url, json=body, headers=headers)
        if response.is_success:
            log.info(
                "posted batch bssid=%s sightings=%d aps=%d status=%s",
                bssid,
                len(sightings),
                len(body["aps"]),
                response.status_code,
            )
            return True
        log.warning(
            "batch rejected status=%s body=%s",
            response.status_code,
            response.text[:500],
        )
        return False
    except httpx.HTTPError as exc:
        log.warning("batch drop (network error): %s", exc)
        return False
    finally:
        if own_client:
            await http.aclose()
