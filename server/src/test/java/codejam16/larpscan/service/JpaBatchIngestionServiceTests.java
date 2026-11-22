package codejam16.larpscan.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codejam16.larpscan.config.LarpProperties;
import codejam16.larpscan.domain.AccessPoint;
import codejam16.larpscan.domain.Batch;
import codejam16.larpscan.repository.AccessPointRepository;
import codejam16.larpscan.repository.BatchRepository;
import codejam16.larpscan.web.dto.BatchRequest;
import codejam16.larpscan.web.dto.BatchRequest.ApReading;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test") //enable test profile
@ExtendWith(OutputCaptureExtension.class)
class JpaBatchIngestionServiceTests {

  private static final Instant NOW = Instant.parse("2026-11-21T14:03:16Z");
  private static final Instant SENT = Instant.parse("2026-11-21T14:03:15Z");
  private static final UUID SCANNER = UUID.fromString("3f2b1a4c-8d9e-4b2a-9c1e-0123456789ab");
  private static final String KNOWN_BSSID = "44:48:c1:a4:1a:50";
  private static final String UNKNOWN_BSSID = "ff:ee:dd:cc:bb:aa";

  @Autowired private TestEntityManager em;
  @Autowired private BatchRepository batches;
  @Autowired private AccessPointRepository accessPoints;

  private JpaBatchIngestionService service;

  @BeforeEach
  void setUp() {
    accessPoints.save(new AccessPoint(KNOWN_BSSID, "trottier", 3));
    LarpProperties props =
        new LarpProperties("key", new LarpProperties.Cors("http://x"), 120, 2.5, 60000, 5,
            3.0, -65, 8.0, -80, 0.8, -40, 3.0);
    service = new JpaBatchIngestionService(
        batches,
        accessPoints,
        new ScannerLocator(new ApLocations(), props),
        Clock.fixed(NOW, ZoneOffset.UTC)
    );
  }

  @Test
  void storesBatchWithRssisAndServerReceiveTime() {
    service.ingest(new BatchRequest(SCANNER, KNOWN_BSSID, SENT, List.of(-67, -72, -80), List.of()));

    Batch stored = onlyBatch();
    assertEquals(SCANNER, stored.getScannerId());
    assertEquals(KNOWN_BSSID, stored.getBssid());
    assertEquals(SENT, stored.getSentAt());
    assertEquals(NOW, stored.getReceivedAt());
    assertArrayEquals(new int[] {-67, -72, -80}, stored.getRssis());
  }

  @Test
  void storesEmptyBatch() {
    service.ingest(new BatchRequest(SCANNER, KNOWN_BSSID, SENT, List.of(), List.of()));

    assertArrayEquals(new int[0], onlyBatch().getRssis());
  }

  @Test
  void storesAndLogsBatchWithUnknownBssid(CapturedOutput output) {
    service.ingest(new BatchRequest(SCANNER, UNKNOWN_BSSID, SENT, List.of(-60), List.of()));

    assertEquals(UNKNOWN_BSSID, onlyBatch().getBssid());
    assertTrue(output.getOut().contains("unknown bssid=" + UNKNOWN_BSSID));
  }

  @Test
  void doesNotLogKnownBssid(CapturedOutput output) {
    service.ingest(new BatchRequest(SCANNER, KNOWN_BSSID, SENT, List.of(-60), List.of()));

    assertFalse(output.getOut().contains("unknown bssid"));
  }

  @Test
  void storesNoPositionWithoutAps() {
    service.ingest(new BatchRequest(SCANNER, KNOWN_BSSID, SENT, List.of(-60), null));

    assertNull(onlyBatch().getLatitude());
    assertNull(onlyBatch().getLongitude());
  }

  @Test
  void storesPositionOfTheOnlyHeardApOnTheConnectedFloor() {
    // 44:48:c1:a4:1a:5x is AP 11 on floor 3; 44:48:c1:a4:4f:1x is AP 0 on floor 5 (ignored)
    service.ingest(new BatchRequest(SCANNER, KNOWN_BSSID, SENT, List.of(-60), List.of(
        new ApReading("44:48:c1:a4:1a:52", -48),
        new ApReading("44:48:c1:a4:4f:10", -40)
    )));

    Batch stored = onlyBatch();
    assertEquals(45.5073446462334, stored.getLatitude(), 1e-9);
    assertEquals(-73.57912498757777, stored.getLongitude(), 1e-9);
  }

  @Test
  void storesNoPositionForUnknownBssid() {
    service.ingest(new BatchRequest(SCANNER, UNKNOWN_BSSID, SENT, List.of(-60), List.of(
        new ApReading("44:48:c1:a4:1a:52", -48)
    )));

    assertNull(onlyBatch().getLatitude());
  }

  private Batch onlyBatch() {
    em.flush();
    em.clear();
    List<Batch> all = batches.findAll();
    assertEquals(1, all.size());
    return all.get(0);
  }
}
