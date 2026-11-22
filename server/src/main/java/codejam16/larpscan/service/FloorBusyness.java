package codejam16.larpscan.service;

import codejam16.larpscan.domain.BusynessLevel;

/**
 * Unsmoothed Busyness of one Floor at one instant, as computed by {@link BusynessCalculator}.
 * Uncovered Floors always carry {@code 0} devices, people and percentage.
 *
 * @param buildingId building identifier
 * @param floor floor number within the Building
 * @param capacity seat capacity of the Floor
 * @param covered whether some Scanner's latest batch in {@code WINDOW} is on this Floor
 * @param devices sum of RSSI counts over the latest batches on this Floor
 * @param estimatedPeople {@code devices / K}, rounded half up
 * @param busynessPct {@code min(100, 100 × devices / K / capacity)}, rounded half up
 */
public record FloorBusyness(
    String buildingId,
    int floor,
    int capacity,
    boolean covered,
    int devices,
    int estimatedPeople,
    int busynessPct
) {

  /**
   * Returns the level for this unsmoothed percentage.
   *
   * @return {@link BusynessLevel#NO_DATA} when uncovered, otherwise the threshold-based level
   */
  public BusynessLevel level() {
    return BusynessLevel.from(covered, busynessPct);
  }
}
