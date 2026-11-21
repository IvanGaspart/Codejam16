package codejam16.larpscan.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codejam16.larpscan.domain.AccessPoint;
import codejam16.larpscan.domain.Batch;
import codejam16.larpscan.repository.AccessPointRepository;
import codejam16.larpscan.repository.BatchRepository;
import codejam16.larpscan.web.dto.BatchRequest;
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
    service = new JpaBatchIngestionService(
        batches, accessPoints, Clock.fixed(NOW, ZoneOffset.UTC)
    );
  }

  @Test
  void storesBatchWithRssisAndServerReceiveTime() {
    service.ingest(new BatchRequest(SCANNER, KNOWN_BSSID, SENT, List.of(-67, -72, -80)));

    Batch stored = onlyBatch();
    assertEquals(SCANNER, stored.getScannerId());
    assertEquals(KNOWN_BSSID, stored.getBssid());
    assertEquals(SENT, stored.getSentAt());
    assertEquals(NOW, stored.getReceivedAt());
    assertArrayEquals(new int[] {-67, -72, -80}, stored.getRssis());
  }

  @Test
  void storesEmptyBatch() {
    service.ingest(new BatchRequest(SCANNER, KNOWN_BSSID, SENT, List.of()));

    assertArrayEquals(new int[0], onlyBatch().getRssis());
  }

  @Test
  void storesAndLogsBatchWithUnknownBssid(CapturedOutput output) {
    service.ingest(new BatchRequest(SCANNER, UNKNOWN_BSSID, SENT, List.of(-60)));

    assertEquals(UNKNOWN_BSSID, onlyBatch().getBssid());
    assertTrue(output.getOut().contains("unknown bssid=" + UNKNOWN_BSSID));
  }

  @Test
  void doesNotLogKnownBssid(CapturedOutput output) {
    service.ingest(new BatchRequest(SCANNER, KNOWN_BSSID, SENT, List.of(-60)));

    assertFalse(output.getOut().contains("unknown bssid"));
  }

  private Batch onlyBatch() {
    em.flush();
    em.clear();
    List<Batch> all = batches.findAll();
    assertEquals(1, all.size());
    return all.get(0);
  }
}
