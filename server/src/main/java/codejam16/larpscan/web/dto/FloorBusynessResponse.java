package codejam16.larpscan.web.dto;

import codejam16.larpscan.domain.BusynessLevel;

/**
 * Current Busyness status for a single Floor returned by
 * {@code GET /api/buildings/{buildingId}/floors}.
 *
 * @param floor floor number within the Building
 * @param capacity total seat capacity of the Floor
 * @param covered whether a batch was received for this Floor within {@code WINDOW}
 * @param estimatedPeople estimated headcount ({@code 0} when uncovered)
 * @param busynessPct percentage of capacity in use, smoothed as the mean of the covered snapshots
 *     among the last {@code SMOOTHING_SNAPSHOTS} (the live value if there are none; {@code 0}
 *     when uncovered)
 * @param level human-readable Busyness classification derived from {@code busynessPct}
 */
public record FloorBusynessResponse(
    int floor,
    int capacity,
    boolean covered,
    int estimatedPeople,
    int busynessPct,
    BusynessLevel level
) {}