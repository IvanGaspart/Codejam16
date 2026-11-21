# Opportunistic Scanners and a stateless window count

**Scanners.** LARP-scan's Scanners are students' own laptops wherever they happen to be, not sensors fixed to each Floor. That keeps hardware cost at zero, which is what killed Waitz at McGill. The trade-off is patchy coverage: a Floor with no Scanner shows "No data" instead of "Quiet". Each Scanner re-reads its AP every batch, because the laptop can move.

**Counting.** _Superseded by [ADR-0002](0002-rssi-only-batches.md)._ BLE addresses rotate about every 15 minutes, so tracking a Device's lifetime (first seen / last seen) means little. Busyness is therefore a stateless count of the distinct addresses heard in a short window (120 s), and there is no per-device table. The window must stay well below the rotation period, or one device is counted twice.
