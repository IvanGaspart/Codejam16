package codejam16.larpscan.web.dto;

import java.time.Instant;

/**
 * Single historical Busyness snapshot entry returned by
 * {@code GET /api/buildings/{buildingId}/floors/{floor}/history}.
 *
 * @param at UTC timestamp when the snapshot was recorded
 * @param busynessPct Busyness percentage in {@code [0, 100]}
 * @param covered whether the Floor had recent Scanner coverage at {@code at}
 */
public record FloorHistoryResponse(
    Instant at,
    int busynessPct,
    boolean covered
) {}