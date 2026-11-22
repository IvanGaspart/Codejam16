package codejam16.larpscan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import codejam16.larpscan.config.LarpProperties;
import codejam16.larpscan.domain.AccessPoint;
import codejam16.larpscan.domain.Batch;
import codejam16.larpscan.domain.BusynessLevel;
import codejam16.larpscan.domain.BusynessSnapshot;
import codejam16.larpscan.domain.Floor;
import codejam16.larpscan.repository.AccessPointRepository;
import codejam16.larpscan.repository.BatchRepository;
import codejam16.larpscan.repository.BusynessSnapshotRepository;
import codejam16.larpscan.repository.FloorRepository;
import codejam16.larpscan.web.dto.BuildingResponse;
import codejam16.larpscan.web.dto.FloorBusynessResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Checks {@code /floors} results and smoothing over snapshots against H2, with
 * {@code WINDOW = 120 s}, {@code K = 2.5} and {@code SMOOTHING_SNAPSHOTS = 5}. A batch of 85
 * devices on Floor 1 (capacity 120) gives a live {@code 28}%.
 */
@DataJpaTest
@ActiveProfiles("test")
class JpaBuildingQueryServiceTests {

  private static final Instant NOW = Instant.parse("2026-11-21T14:03:16Z");
  private static final String BUILDING = "trottier";
  private static final String AP_FLOOR_1 = "44:48:c1:a4:1a:50";
  private static final UUID SCANNER = UUID.fromString("3f2b1a4c-8d9e-4b2a-9c1e-0123456789ab");

  @Autowired private BatchRepository batches;
  @Autowired private FloorRepository floors;
  @Autowired private AccessPointRepository accessPoints;
  @Autowired private BusynessSnapshotRepository snapshots;

  private JpaBuildingQueryService service;

  @BeforeEach
  void setUp() {
    floors.save(new Floor(BUILDING, 2, 90));
    floors.save(new Floor(BUILDING, 1, 120));
    accessPoints.save(new AccessPoint(AP_FLOOR_1, BUILDING, 1));
    LarpProperties props =
        new LarpProperties("key", new LarpProperties.Cors("http://x"), 120, 2.5, 60000, 5,
            3.0, -65, 8.0, -80, 0.8, -40, 3.0);
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    service = new JpaBuildingQueryService(
        new BusynessCalculator(batches, floors, accessPoints, props),
        new FloorHistoryService(snapshots, floors, clock),
        floors,
        snapshots,
        props,
        batches,
        clock
    );
  }

  @Test
  void listsTrottier() {
    assertEquals(
        List.of(new BuildingResponse("trottier", "Trottier Building")),
        service.listBuildings()
    );
  }

  @Test
  void fallsBackToLivePctWithoutSnapshots() {
    coverFloor1();

    assertEquals(
        List.of(
            new FloorBusynessResponse(1, 120, true, 1, 34, 28, BusynessLevel.QUIET),
            new FloorBusynessResponse(2, 90, false, 0, 0, 0, BusynessLevel.NO_DATA)
        ),
        service.listFloors(BUILDING)
    );
  }

  @Test
  void fallsBackToLivePctWhenRecentSnapshotsAreAllUncovered() {
    coverFloor1();
    snapshot(1, 1, 0, false);
    snapshot(1, 2, 0, false);

    assertEquals(28, floor(1).busynessPct());
  }

  @Test
  void averagesTheLastSmoothingSnapshotsOfTheFloor() {
    coverFloor1();
    snapshot(1, 1, 10, true);
    snapshot(1, 2, 20, true);
    snapshot(1, 3, 30, true);
    snapshot(1, 4, 40, true);
    snapshot(1, 5, 50, true);
    snapshot(1, 6, 100, true);
    snapshot(2, 1, 90, true);

    FloorBusynessResponse f1 = floor(1);
    assertEquals(30, f1.busynessPct());
    assertEquals(BusynessLevel.MODERATE, f1.level());
    assertEquals(36, f1.estimatedPeople()); // smoothed like the pct, not the live 34
    assertEquals(true, f1.covered());
  }

  @Test
  void smoothsStoredPeopleAndDerivesPctFromThem() {
    coverFloor1();
    snapshotPeople(1, 1, 0.0, true);
    snapshotPeople(1, 2, 0.0, true);
    snapshotPeople(1, 3, 0.0, true);
    snapshotPeople(1, 4, 28.8, true);
    snapshotPeople(1, 5, 31.2, true);

    FloorBusynessResponse f1 = floor(1);
    assertEquals(12, f1.estimatedPeople()); // mean 12.0 people
    assertEquals(10, f1.busynessPct()); // 100 × 12 / 120
    assertEquals(1, f1.scanners());
  }

  @Test
  void keepsPeopleAboveCapacityWhilePctCapsAt100() {
    coverFloor1();
    snapshotPeople(1, 1, 150.0, true);
    snapshotPeople(1, 2, 162.0, true);

    FloorBusynessResponse f1 = floor(1);
    assertEquals(156, f1.estimatedPeople());
    assertEquals(100, f1.busynessPct());
  }

  @Test
  void derivesPeopleFromPctForSnapshotsStoredWithoutPeople() {
    coverFloor1();
    snapshot(1, 1, 50, true); // legacy row: 60 people at capacity 120
    snapshotPeople(1, 2, 24.0, true);

    FloorBusynessResponse f1 = floor(1);
    assertEquals(42, f1.estimatedPeople());
    assertEquals(35, f1.busynessPct());
  }

  @Test
  void leavesUncoveredSnapshotsOutOfTheMean() {
    coverFloor1();
    snapshot(1, 1, 40, true);
    snapshot(1, 2, 0, false);
    snapshot(1, 3, 50, true);
    snapshot(1, 4, 0, false);
    snapshot(1, 5, 0, false);
    snapshot(1, 6, 100, true);

    assertEquals(45, floor(1).busynessPct());
  }

  @Test
  void reportsNoDataWhenLiveCoverageIsMissingDespiteSnapshots() {
    snapshot(2, 1, 80, true);
    snapshot(2, 2, 80, true);

    assertEquals(
        new FloorBusynessResponse(2, 90, false, 0, 0, 0, BusynessLevel.NO_DATA),
        floor(2)
    );
  }

  @Test
  void derivesLevelFromSmoothedPct() {
    coverFloor1();
    snapshot(1, 1, 84, true);
    snapshot(1, 2, 86, true);

    FloorBusynessResponse f1 = floor(1);
    assertEquals(85, f1.busynessPct());
    assertEquals(BusynessLevel.FULL, f1.level());
  }

  @Test
  void roundsTheMeanHalfUp() {
    coverFloor1();
    snapshot(1, 1, 10, true);
    snapshot(1, 2, 11, true);
    assertEquals(11, floor(1).busynessPct());

    snapshot(1, 3, 10, true);
    assertEquals(10, floor(1).busynessPct());
  }

  @Test
  void rejectsUnknownBuilding() {
    assertThrows(BuildingNotFoundException.class, () -> service.listFloors("redpath"));
  }

  private void coverFloor1() {
    Instant recent = NOW.minusSeconds(30);
    batches.save(new Batch(SCANNER, AP_FLOOR_1, recent, recent, new int[85]));
  }

  private void snapshot(int floor, int minutesAgo, int pct, boolean covered) {
    snapshots.save(
        new BusynessSnapshot(BUILDING, floor, NOW.minusSeconds(60L * minutesAgo), pct, covered)
    );
  }

  private void snapshotPeople(int floor, int minutesAgo, double people, boolean covered) {
    int pct = (int) Math.min(100, Math.round(100 * people / 120));
    snapshots.save(new BusynessSnapshot(
        BUILDING, floor, NOW.minusSeconds(60L * minutesAgo), pct, people, covered
    ));
  }

  private FloorBusynessResponse floor(int floor) {
    return service.listFloors(BUILDING).stream()
        .filter(f -> f.floor() == floor)
        .findFirst()
        .orElseThrow();
  }
}
