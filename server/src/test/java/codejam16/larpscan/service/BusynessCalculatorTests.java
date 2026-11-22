package codejam16.larpscan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codejam16.larpscan.config.LarpProperties;
import codejam16.larpscan.domain.AccessPoint;
import codejam16.larpscan.domain.Batch;
import codejam16.larpscan.domain.BusynessLevel;
import codejam16.larpscan.domain.Floor;
import codejam16.larpscan.repository.AccessPointRepository;
import codejam16.larpscan.repository.BatchRepository;
import codejam16.larpscan.repository.FloorRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Checks the raw Busyness and coverage calculation against H2, with {@code WINDOW = 120 s},
 * {@code K = 2.5} and the default strata ({@code CLOSE_D = 3 m} at {@code -65} dBm,
 * {@code FAR_D = 8 m} down to {@code -80} dBm, far devices weighing {@code 0.8}). Batches built by
 * {@link #batch} are positionless and all their devices are close.
 */
@DataJpaTest
@ActiveProfiles("test")
class BusynessCalculatorTests {

  private static final Instant NOW = Instant.parse("2026-11-21T14:03:16Z");
  private static final String BUILDING = "trottier";
  private static final String AP_FLOOR_1 = "44:48:c1:a4:1a:50";
  private static final String AP_FLOOR_2 = "44:48:c1:a4:2b:60";
  private static final String UNKNOWN_AP = "ff:ee:dd:cc:bb:aa";
  private static final UUID SCANNER_A = UUID.fromString("3f2b1a4c-8d9e-4b2a-9c1e-0123456789ab");
  private static final UUID SCANNER_B = UUID.fromString("7a1c2d3e-4f50-4617-8293-a4b5c6d7e8f9");
  private static final double LAT = 45.5074;
  private static final double LON = -73.5791;
  private static final double EPS = 1e-9;

  @Autowired private BatchRepository batches;
  @Autowired private FloorRepository floors;
  @Autowired private AccessPointRepository accessPoints;

  private BusynessCalculator calculator;

  @BeforeEach
  void setUp() {
    floors.save(new Floor(BUILDING, 2, 10));
    floors.save(new Floor(BUILDING, 1, 100));
    floors.save(new Floor(BUILDING, 3, 50));
    accessPoints.save(new AccessPoint(AP_FLOOR_1, BUILDING, 1));
    accessPoints.save(new AccessPoint(AP_FLOOR_2, BUILDING, 2));
    LarpProperties props =
        new LarpProperties("key", new LarpProperties.Cors("http://x"), 120, 2.5, 60000, 5,
            3.0, -65, 8.0, -80, 0.8, -40, 3.0);
    calculator = new BusynessCalculator(batches, floors, accessPoints, props);
  }

  @Test
  void returnsEveryFloorOrderedAndUncoveredWithoutBatches() {
    List<FloorBusyness> result = calculator.calculate(BUILDING, NOW);

    assertEquals(List.of(1, 2, 3), result.stream().map(FloorBusyness::floor).toList());
    for (FloorBusyness f : result) {
      assertNoData(f);
    }
    assertEquals(100, result.get(0).capacity());
  }

  @Test
  void singleScannerDividesDevicesByKAndCapacity() {
    batch(SCANNER_A, AP_FLOOR_1, 10, 25);

    FloorBusyness f1 = floor(1);
    assertTrue(f1.covered());
    assertEquals(1, f1.scanners());
    assertEquals(25, f1.devices());
    assertEquals(10, f1.estimatedPeople());
    assertEquals(10, f1.busynessPct());
    assertEquals(BusynessLevel.QUIET, f1.level());
  }

  @Test
  void roundsPeopleHalfUpAndPercentFromUnroundedPeople() {
    batch(SCANNER_A, AP_FLOOR_2, 10, 3);

    FloorBusyness f2 = floor(2);
    assertEquals(1, f2.estimatedPeople()); // 1.2
    assertEquals(12, f2.busynessPct()); // 100 * 1.2 / 10, not 100 * 1 / 10
  }

  @Test
  void takesMaxOfPositionlessScannersOnTheSameFloor() {
    batch(SCANNER_A, AP_FLOOR_1, 10, 10);
    batch(SCANNER_B, AP_FLOOR_1, 20, 15);

    FloorBusyness f1 = floor(1);
    assertEquals(2, f1.scanners());
    assertEquals(15, f1.devices());
    assertEquals(6, f1.estimatedPeople());
  }

  @Test
  void countsOnlyEachScannersLatestBatch() {
    batch(SCANNER_A, AP_FLOOR_1, 90, 50);
    batch(SCANNER_A, AP_FLOOR_1, 60, 40);
    batch(SCANNER_A, AP_FLOOR_1, 10, 5);

    assertEquals(5, floor(1).devices());
    assertEquals(2, floor(1).estimatedPeople());
  }

  @Test
  void ignoresBatchesOutsideTheWindow() {
    batch(SCANNER_A, AP_FLOOR_1, 120, 25); // exactly t − WINDOW: excluded
    batch(SCANNER_B, AP_FLOOR_2, 500, 25);

    assertNoData(floor(1));
    assertNoData(floor(2));
  }

  @Test
  void includesBatchReceivedExactlyAtNow() {
    batch(SCANNER_A, AP_FLOOR_1, 0, 25);

    assertTrue(floor(1).covered());
  }

  @Test
  void ignoresBatchesReceivedAfterTheInstant() {
    batches.save(new Batch(SCANNER_A, AP_FLOOR_1, NOW, NOW.plusSeconds(1), rssis(25)));

    assertNoData(floor(1));
  }

  @Test
  void scannerThatMovedCountsOnlyOnItsNewFloor() {
    batch(SCANNER_A, AP_FLOOR_1, 60, 25);
    batch(SCANNER_A, AP_FLOOR_2, 10, 5);

    assertNoData(floor(1));
    assertTrue(floor(2).covered());
    assertEquals(1, floor(2).scanners());
    assertEquals(5, floor(2).devices());
  }

  @Test
  void unknownBssidGivesNoCoverageOrCount() {
    batch(SCANNER_A, UNKNOWN_AP, 10, 25);

    for (FloorBusyness f : calculator.calculate(BUILDING, NOW)) {
      assertNoData(f);
    }
  }

  @Test
  void scannerWhoseLatestBatchIsUnknownCountsNowhere() {
    batch(SCANNER_A, AP_FLOOR_1, 60, 25);
    batch(SCANNER_A, UNKNOWN_AP, 10, 25);
    batch(SCANNER_B, AP_FLOOR_2, 10, 5);

    assertNoData(floor(1));
    assertEquals(1, floor(2).scanners());
    assertEquals(5, floor(2).devices());
  }

  @Test
  void emptyBatchGivesCoverageAtZeroPercent() {
    batch(SCANNER_A, AP_FLOOR_1, 10, 0);

    FloorBusyness f1 = floor(1);
    assertTrue(f1.covered());
    assertEquals(0, f1.estimatedPeople());
    assertEquals(0, f1.busynessPct());
    assertEquals(BusynessLevel.QUIET, f1.level());
  }

  @Test
  void capsPercentAtOneHundredButKeepsPeopleEstimate() {
    batch(SCANNER_A, AP_FLOOR_2, 10, 50); // 20 people on a 10-seat Floor

    FloorBusyness f2 = floor(2);
    assertEquals(20, f2.estimatedPeople());
    assertEquals(100, f2.busynessPct());
    assertEquals(BusynessLevel.FULL, f2.level());
  }

  @ParameterizedTest
  @CsvSource({"72, 29, QUIET", "75, 30, MODERATE", "150, 60, BUSY", "212, 85, FULL"})
  void appliesLevelThresholds(int devices, int pct, BusynessLevel level) {
    batch(SCANNER_A, AP_FLOOR_1, 10, devices); // capacity 100

    assertEquals(pct, floor(1).busynessPct());
    assertEquals(level, floor(1).level());
  }

  @Test
  void unknownBuildingHasNoFloors() {
    assertTrue(calculator.calculate("redpath", NOW).isEmpty());
  }

  @Test
  void countsFarDevicesDownscaledAndIgnoresWeakerOnes() {
    // 2 close + 3 far (one exactly at FAR_RSSI) + 1 below FAR_RSSI: 2 + 0.8 × 3
    assertEquals(4.4, calculator.estimateDevices(List.of(
        located(SCANNER_A, null, null, -65, -50, -66, -70, -80, -81))), EPS);
  }

  @Test
  void singlePositionedScannerCountsItsWeight() {
    assertEquals(14, calculator.estimateDevices(List.of(located(SCANNER_A, LAT, LON))), EPS);
  }

  @Test
  void coincidentScannersWithTheSameCountsCountOnce() {
    assertEquals(14, calculator.estimateDevices(List.of(
        located(SCANNER_A, LAT, LON), located(SCANNER_B, LAT, LON))), EPS);
  }

  @Test
  void disjointScannersAddUp() {
    // ~111 m apart: far discs (8 m) do not touch
    assertEquals(28, calculator.estimateDevices(List.of(
        located(SCANNER_A, LAT, LON), located(SCANNER_B, LAT + 0.001, LON))), EPS);
  }

  @Test
  void partiallyOverlappingScannersCountBetweenOneAndBoth() {
    // ~5.6 m apart: far discs overlap partly
    double devices = calculator.estimateDevices(List.of(
        located(SCANNER_A, LAT, LON), located(SCANNER_B, LAT + 0.00005, LON)));
    assertTrue(devices > 14 && devices < 28, "devices=" + devices);
  }

  @Test
  void positionlessScannerFallsBackToMaxWithTheUnion() {
    Batch positioned = located(SCANNER_A, LAT, LON); // 14
    UUID scannerC = UUID.fromString("0b1c2d3e-4f50-4617-8293-a4b5c6d7e8f9");

    assertEquals(20, calculator.estimateDevices(List.of(
        positioned, batchOf(SCANNER_B, rssis(20)))), EPS);
    assertEquals(14, calculator.estimateDevices(List.of(
        positioned, batchOf(SCANNER_B, rssis(5)), batchOf(scannerC, rssis(3)))), EPS);
  }

  @Test
  void calculateUsesTheStoredPosition() {
    batches.save(located(SCANNER_A, LAT, LON));
    batches.save(located(SCANNER_B, LAT, LON));

    FloorBusyness f1 = floor(1);
    assertEquals(2, f1.scanners());
    assertEquals(14, f1.devices(), EPS);
    assertEquals(6, f1.estimatedPeople()); // 5.6
  }

  /** A batch on floor 1, received just now: 10 close and 5 far devices by default ({@code w = 14}). */
  private static Batch located(UUID scanner, Double lat, Double lon, int... rssis) {
    if (rssis.length == 0) {
      rssis = new int[] {-50, -50, -50, -50, -50, -60, -60, -60, -60, -65, -70, -70, -75, -79, -80};
    }
    return new Batch(scanner, AP_FLOOR_1, NOW, NOW, rssis, lat, lon);
  }

  private static Batch batchOf(UUID scanner, int[] rssis) {
    return new Batch(scanner, AP_FLOOR_1, NOW, NOW, rssis);
  }

  private void batch(UUID scanner, String bssid, int secondsAgo, int devices) {
    Instant at = NOW.minusSeconds(secondsAgo);
    batches.save(new Batch(scanner, bssid, at, at, rssis(devices)));
  }

  private static int[] rssis(int devices) {
    int[] rssis = new int[devices];
    Arrays.fill(rssis, -60);
    return rssis;
  }

  private FloorBusyness floor(int floor) {
    return calculator.calculate(BUILDING, NOW).stream()
        .filter(f -> f.floor() == floor)
        .findFirst()
        .orElseThrow();
  }

  private static void assertNoData(FloorBusyness f) {
    assertFalse(f.covered());
    assertEquals(0, f.scanners());
    assertEquals(0, f.devices());
    assertEquals(0, f.estimatedPeople());
    assertEquals(0, f.busynessPct());
    assertEquals(BusynessLevel.NO_DATA, f.level());
  }
}
