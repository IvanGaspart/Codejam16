# RSSI-only batches and a latest-batch count

**Context.** macOS does not expose BLE MAC addresses: CoreBluetooth gives each device a UUID that each Mac makes up for itself. So the same phone looks different to a Linux Scanner and to every Mac Scanner, and the server could never match devices across Scanners. It also never used the addresses for anything but counting.

**Decision.** Scanners send no device identifiers. Each batch carries one RSSI per distinct device heard during the interval; the Scanner deduplicates locally, where it can still see addresses. The server stores the RSSIs as an array on `batch`, and there is no `sighting` table. A Floor's device count is the sum, over Scanners, of the RSSI count in each Scanner's latest batch within `WINDOW`.

**Consequences.** This supersedes the counting part of [ADR-0001](0001-opportunistic-scanners-and-window-count.md): `WINDOW` now only bounds how stale a Scanner's latest batch may be, and is no longer tied to address rotation. Two Scanners on one Floor that hear the same device both count it. RQ-12 (strongest-RSSI resolution across Scanners) is withdrawn, since it needs identifiers.
