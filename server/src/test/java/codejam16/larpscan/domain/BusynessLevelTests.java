package codejam16.larpscan.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BusynessLevelTests {

  @Test
  void mapsCoverageAndThresholdsToExpectedLevels() {
    assertEquals(BusynessLevel.NO_DATA, BusynessLevel.from(false, 0));
    assertEquals(BusynessLevel.NO_DATA, BusynessLevel.from(false, 75));
    assertEquals(BusynessLevel.QUIET, BusynessLevel.from(true, 0));
    assertEquals(BusynessLevel.QUIET, BusynessLevel.from(true, 29));
    assertEquals(BusynessLevel.MODERATE, BusynessLevel.from(true, 30));
    assertEquals(BusynessLevel.MODERATE, BusynessLevel.from(true, 59));
    assertEquals(BusynessLevel.BUSY, BusynessLevel.from(true, 60));
    assertEquals(BusynessLevel.BUSY, BusynessLevel.from(true, 84));
    assertEquals(BusynessLevel.FULL, BusynessLevel.from(true, 85));
    assertEquals(BusynessLevel.FULL, BusynessLevel.from(true, 100));
  }
}