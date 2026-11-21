package codejam16.larpscan.domain;

/**
 * Human-readable busyness classification for a Floor.
 */
public enum BusynessLevel {
  QUIET,
  MODERATE,
  BUSY,
  FULL,
  NO_DATA;

  /**
   * Maps coverage status and smoothed busyness percentage to a {@link BusynessLevel}.
   *
   * @param covered whether at least one batch arrived from an AP on the Floor within {@code WINDOW}
   * @param busynessPct percentage of Floor capacity in use ({@code 0..100})
   * @return {@link #NO_DATA} when uncovered, otherwise the threshold-based level
   */
  public static BusynessLevel from(boolean covered, int busynessPct) {
    if (!covered) {
      return NO_DATA;
    }
    if (busynessPct < 30) {
      return QUIET;
    }
    if (busynessPct < 60) {
      return MODERATE;
    }
    if (busynessPct < 85) {
      return BUSY;
    }
    return FULL;
  }
}