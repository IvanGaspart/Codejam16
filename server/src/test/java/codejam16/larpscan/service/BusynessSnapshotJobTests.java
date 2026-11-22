package codejam16.larpscan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codejam16.larpscan.config.LarpProperties;
import codejam16.larpscan.domain.AccessPoint;
import codejam16.larpscan.domain.Batch;
import codejam16.larpscan.domain.BusynessSnapshot;
import codejam16.larpscan.domain.Floor;
import codejam16.larpscan.repository.AccessPointRepository;
import codejam16.larpscan.repository.BatchRepository;
import codejam16.larpscan.repository.BusynessSnapshotRepository;
import codejam16.larpscan.repository.FloorRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Checks that the snapshot job stores the raw Busyness of every Floor at the clock instant, with
 * {@code WINDOW = 120 s} and {@code K = 2.5}.
 */
@DataJpaTest
@ActiveProfiles("test")
class BusynessSnapshotJobTests {

  private static final Instant NOW = Instant.parse("2026-11-21T14:03:16Z");
  private static final String BUILDING = "trottier";
  private static final String AP_FLOOR_1 = "44:48:c1:a4:1a:50";
  private static final UUID SCANNER = UUID.fromString("3f2b1a4c-8d9e-4b2a-9c1e-0123456789ab");

  @Autowired private BatchRepository batches;
  @Autowired private FloorRepository floors;
  @Autowired private AccessPointRepository accessPoints;
  @Autowired private BusynessSnapshotRepository snapshots;

  private BusynessSnapshotJob job;

  @BeforeEach
  void setUp() {
    floors.save(new Floor(BUILDING, 2, 90));
    floors.save(new Floor(BUILDING, 1, 120));
    floors.save(new Floor("redpath", 1, 50));
    accessPoints.save(new AccessPoint(AP_FLOOR_1, BUILDING, 1));
    LarpProperties props =
        new LarpProperties("key", new LarpProperties.Cors("http://x"), 120, 2.5, 60000, 5);
    job = new BusynessSnapshotJob(
        new BusynessCalculator(batches, floors, accessPoints, props),
        snapshots,
        Clock.fixed(NOW, ZoneOffset.UTC)
    );
  }

  @Test
  void storesOneRawSnapshotPerTrottierFloorAtTheClockInstant() {
    Instant recent = NOW.minusSeconds(30);
    batches.save(new Batch(SCANNER, AP_FLOOR_1, recent, recent, new int[85]));

    job.snapshot();

    List<BusynessSnapshot> stored = stored();
    assertEquals(2, stored.size());

    BusynessSnapshot f1 = stored.get(0);
    assertEquals(BUILDING, f1.getBuildingId());
    assertEquals(1, f1.getFloor());
    assertEquals(NOW, f1.getAt());
    assertEquals(28, f1.getBusynessPct());
    assertEquals(34.0, f1.getEstimatedPeople()); // 85 / K, unrounded
    assertTrue(f1.isCovered());

    BusynessSnapshot f2 = stored.get(1);
    assertEquals(BUILDING, f2.getBuildingId());
    assertEquals(2, f2.getFloor());
    assertEquals(NOW, f2.getAt());
    assertEquals(0, f2.getBusynessPct());
    assertEquals(0.0, f2.getEstimatedPeople());
    assertFalse(f2.isCovered());
  }

  @Test
  void storesUncoveredSnapshotsWithoutBatches() {
    job.snapshot();

    List<BusynessSnapshot> stored = stored();
    assertEquals(List.of(1, 2), stored.stream().map(BusynessSnapshot::getFloor).toList());
    for (BusynessSnapshot s : stored) {
      assertFalse(s.isCovered());
      assertEquals(0, s.getBusynessPct());
    }
  }

  @Test
  void keepsEarlierSnapshots() {
    job.snapshot();
    job.snapshot();

    assertEquals(4, snapshots.count());
  }

  private List<BusynessSnapshot> stored() {
    return snapshots.findAll().stream()
        .sorted(Comparator.comparing(BusynessSnapshot::getFloor))
        .toList();
  }
}
