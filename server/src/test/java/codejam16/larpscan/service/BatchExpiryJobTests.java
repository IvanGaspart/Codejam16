package codejam16.larpscan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import codejam16.larpscan.config.LarpProperties;
import codejam16.larpscan.domain.Batch;
import codejam16.larpscan.repository.BatchRepository;
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
 * Runs the expiry job directly (not through the scheduler) with a fixed clock.
 */
@DataJpaTest
@ActiveProfiles("test") //enable test profile
class BatchExpiryJobTests {

  private static final Instant NOW = Instant.parse("2026-11-21T14:03:16Z");
  private static final int WINDOW_SECONDS = 120;
  private static final Instant CUTOFF = NOW.minusSeconds(WINDOW_SECONDS);
  private static final UUID SCANNER = UUID.fromString("3f2b1a4c-8d9e-4b2a-9c1e-0123456789ab");

  @Autowired private BatchRepository batches;

  private BatchExpiryJob job;

  @BeforeEach
  void setUp() {
    LarpProperties properties = new LarpProperties(
        "test-key", new LarpProperties.Cors("http://localhost"), WINDOW_SECONDS, 2.5, 60000, 5
    );
    job = new BatchExpiryJob(batches, properties, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void deletesBatchesOlderThanWindow() {
    save(CUTOFF.minusSeconds(1));
    save(CUTOFF.minusSeconds(3600));

    assertEquals(2, job.expire());
    assertEquals(0, batches.count());
  }

  @Test
  void keepsBatchesWithinWindow() {
    save(CUTOFF.plusMillis(1));
    save(NOW);

    assertEquals(0, job.expire());
    assertEquals(2, batches.count());
  }

  @Test
  void deletesBatchExactlyAtWindowBoundary() {
    save(CUTOFF);
    Batch kept = save(CUTOFF.plusSeconds(1));

    assertEquals(1, job.expire());
    List<Batch> remaining = batches.findAll();
    assertEquals(1, remaining.size());
    assertEquals(kept.getId(), remaining.get(0).getId());
  }

  @Test
  void emptyTableIsNoOp() {
    assertEquals(0, job.expire());
    assertEquals(0, batches.count());
  }

  private Batch save(Instant receivedAt) {
    return batches.save(
        new Batch(SCANNER, "44:48:c1:a4:1a:50", receivedAt, receivedAt, new int[] {-70})
    );
  }
}
