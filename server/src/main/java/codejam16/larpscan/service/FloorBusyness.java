package codejam16.larpscan.service;

import codejam16.larpscan.domain.BusynessLevel;

/**
 * Unsmoothed Busyness of one Floor at one instant, as computed by {@link BusynessCalculator}.
 * Uncovered Floors always carry {@code 0} devices, people, percentage and scanners.
 *
 * @param buildingId building identifier
 * @param floor floor number within the Building
 * @param capacity seat capacity of the Floor
 * @param covered whether some Scanner's latest batch in {@code WINDOW} is on this Floor
 * @param scanners count of Scanners whose latest in-window batch is on this Floor ({@code 0} when
 *     uncovered)
 * @param devices estimated distinct devices on this Floor (strata-weighted union of the
 *     Scanners' latest batches; see {@link BusynessCalculator})
 * @param people {@code devices / K}, unrounded; stored in snapshots so smoothing can average it
 * @param estimatedPeople {@code devices / K}, rounded half up
 * @param busynessPct {@code min(100, 100 × devices / K / capacity)}, rounded half up
 */
public record FloorBusyness(
    String buildingId,
    int floor,
    int capacity,
    boolean covered,
    int scanners,
    double devices,
    double people,
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
