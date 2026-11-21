package codejam16.larpscan.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codejam16.larpscan.domain.AccessPoint;
import codejam16.larpscan.domain.Batch;
import codejam16.larpscan.domain.BusynessSnapshot;
import codejam16.larpscan.domain.Floor;
import codejam16.larpscan.domain.Sighting;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * Round-trips every entity through H2 to check the schema Hibernate generates. Each save is
 * flushed and the persistence context cleared, so reads come from the database, not the cache.
 */
@DataJpaTest
@ActiveProfiles("test") //enable test profile
class EntityPersistenceTests {

  private static final Instant T = Instant.parse("2026-11-21T14:03:15Z");
  private static final UUID SCANNER = UUID.fromString("3f2b1a4c-8d9e-4b2a-9c1e-0123456789ab");

  @Autowired private TestEntityManager em;
  @Autowired private FloorRepository floors;
  @Autowired private AccessPointRepository accessPoints;
  @Autowired private BatchRepository batches;
  @Autowired private SightingRepository sightings;
  @Autowired private BusynessSnapshotRepository snapshots;

  @Test
  void savesAndReloadsFloor() {
    Long id = floors.save(new Floor("trottier", 1, 120)).getId();
    em.flush();
    em.clear();

    Floor loaded = floors.findById(id).orElseThrow();
    assertEquals("trottier", loaded.getBuildingId());
    assertEquals(1, loaded.getFloor());
    assertEquals(120, loaded.getCapacity());
  }

  @Test
  void rejectsDuplicateFloorInSameBuilding() {
    floors.save(new Floor("trottier", 1, 120));

    assertThrows(
        DataIntegrityViolationException.class,
        () -> floors.saveAndFlush(new Floor("trottier", 1, 90))
    );
  }

  @Test
  void savesAndReloadsAccessPointByBssid() {
    accessPoints.save(new AccessPoint("44:48:c1:a4:1a:50", "trottier", 3));
    em.flush();
    em.clear();

    AccessPoint loaded = accessPoints.findById("44:48:c1:a4:1a:50").orElseThrow();
    assertEquals("trottier", loaded.getBuildingId());
    assertEquals(3, loaded.getFloor());
  }

  @Test
  void savesBatchWithUnknownBssid() {
    // No AccessPoint exists for this BSSID; the batch must still be stored (RQ-03)
    Long id = batches.save(new Batch(SCANNER, "ff:ee:dd:cc:bb:aa", T)).getId();
    em.flush();
    em.clear();

    Batch loaded = batches.findById(id).orElseThrow();
    assertEquals(SCANNER, loaded.getScannerId());
    assertEquals("ff:ee:dd:cc:bb:aa", loaded.getBssid());
    assertEquals(T, loaded.getReceivedAt());
  }

  @Test
  void savesSightingLinkedToItsBatch() {
    Batch batch = batches.save(new Batch(SCANNER, "44:48:c1:a4:1a:50", T));
    Long id = sightings.save(
        new Sighting(batch, "5e:aa:01:23:45:67", -67, T.minusSeconds(6))
    ).getId();
    em.flush();
    em.clear();

    Sighting loaded = sightings.findById(id).orElseThrow();
    assertEquals("5e:aa:01:23:45:67", loaded.getAddress());
    assertEquals(-67, loaded.getRssi());
    assertEquals(T.minusSeconds(6), loaded.getSeenAt());
    assertEquals(batch.getId(), loaded.getBatch().getId());
  }

  @Test
  void rejectsSightingWithoutBatch() {
    assertThrows(
        DataIntegrityViolationException.class,
        () -> sightings.saveAndFlush(new Sighting(null, "5e:aa:01:23:45:67", -67, T))
    );
  }

  @Test
  void savesAndReloadsBusynessSnapshot() {
    Long id = snapshots.save(new BusynessSnapshot("trottier", 2, T, 28, true)).getId();
    em.flush();
    em.clear();

    BusynessSnapshot loaded = snapshots.findById(id).orElseThrow();
    assertNotNull(loaded.getId());
    assertEquals("trottier", loaded.getBuildingId());
    assertEquals(2, loaded.getFloor());
    assertEquals(T, loaded.getAt());
    assertEquals(28, loaded.getBusynessPct());
    assertTrue(loaded.isCovered());
  }
}
