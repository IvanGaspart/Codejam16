package codejam16.larpscan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codejam16.larpscan.domain.BusynessSnapshot;
import codejam16.larpscan.domain.Floor;
import codejam16.larpscan.repository.BusynessSnapshotRepository;
import codejam16.larpscan.repository.FloorRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test") //enable test profile
class FloorHistoryServiceTests {

  // 03:00Z on Nov 21 is still 22:00 on Nov 20 in Montreal (EST, UTC-5).
  private static final Instant NOW = Instant.parse("2026-11-21T03:00:00Z");
  private static final Instant MONTREAL_MIDNIGHT = Instant.parse("2026-11-20T05:00:00Z");
  private static final String TROTTIER = "trottier";

  @Autowired private TestEntityManager em;
  @Autowired private BusynessSnapshotRepository snapshots;
  @Autowired private FloorRepository floors;

  private FloorHistoryService service;

  @BeforeEach
  void setUp() {
    floors.save(new Floor(TROTTIER, 3, 100));
    floors.save(new Floor(TROTTIER, 4, 100));
    floors.save(new Floor("mclennan", 3, 100));
    service = new FloorHistoryService(snapshots, floors, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void returnsSnapshotsOrderedByAt() {
    save(TROTTIER, 3, "2026-11-21T02:02:00Z", 30);
    save(TROTTIER, 3, "2026-11-21T02:00:00Z", 10);
    save(TROTTIER, 3, "2026-11-21T02:01:00Z", 20);

    List<BusynessSnapshot> result = history(Instant.parse("2026-11-21T00:00:00Z"));

    assertEquals(List.of(10, 20, 30), pcts(result));
  }

  @Test
  void sinceIsInclusive() {
    save(TROTTIER, 3, "2026-11-21T01:59:00Z", 10);
    save(TROTTIER, 3, "2026-11-21T02:00:00Z", 20);
    save(TROTTIER, 3, "2026-11-21T02:01:00Z", 30);

    List<BusynessSnapshot> result = history(Instant.parse("2026-11-21T02:00:00Z"));

    assertEquals(List.of(20, 30), pcts(result));
  }

  @Test
  void excludesOtherFloorsAndBuildings() {
    save(TROTTIER, 3, "2026-11-21T02:00:00Z", 10);
    save(TROTTIER, 4, "2026-11-21T02:00:00Z", 40);
    save("mclennan", 3, "2026-11-21T02:00:00Z", 50);

    List<BusynessSnapshot> result = history(Instant.parse("2026-11-21T00:00:00Z"));

    assertEquals(1, result.size());
    assertEquals(TROTTIER, result.get(0).getBuildingId());
    assertEquals(3, result.get(0).getFloor());
    assertEquals(10, result.get(0).getBusynessPct());
  }

  @Test
  void defaultsSinceToMontrealLocalMidnight() {
    save(TROTTIER, 3, "2026-11-20T04:59:00Z", 10); // 23:59 Nov 19 in Montreal
    save(TROTTIER, 3, "2026-11-20T05:00:00Z", 20); // 00:00 Nov 20 in Montreal
    save(TROTTIER, 3, "2026-11-21T02:59:00Z", 30);

    List<BusynessSnapshot> result = history(null);

    assertEquals(MONTREAL_MIDNIGHT, result.get(0).getAt());
    assertEquals(List.of(20, 30), pcts(result));
  }

  @Test
  void returnsEmptyListWhenNoSnapshots() {
    assertTrue(history(null).isEmpty());
  }

  @Test
  void returnsEmptyListForUnknownFloor() {
    save(TROTTIER, 3, "2026-11-21T02:00:00Z", 10);

    assertTrue(service.history(TROTTIER, 99, null).isEmpty());
  }

  @Test
  void throwsForUnknownBuilding() {
    BuildingNotFoundException e = assertThrows(
        BuildingNotFoundException.class, () -> service.history("nowhere", 3, null)
    );
    assertEquals("nowhere", e.getBuildingId());
  }

  private List<BusynessSnapshot> history(Instant since) {
    em.flush();
    em.clear();
    return service.history(TROTTIER, 3, since);
  }

  private void save(String buildingId, int floor, String at, int pct) {
    snapshots.save(new BusynessSnapshot(buildingId, floor, Instant.parse(at), pct, true));
  }

  private static List<Integer> pcts(List<BusynessSnapshot> result) {
    return result.stream().map(BusynessSnapshot::getBusynessPct).toList();
  }
}
