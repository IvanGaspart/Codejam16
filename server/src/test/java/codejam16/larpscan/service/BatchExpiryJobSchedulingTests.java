package codejam16.larpscan.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codejam16.larpscan.domain.Batch;
import codejam16.larpscan.repository.BatchRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Calls the scheduler entry point on the real Spring bean, outside any test transaction, the way
 * the scheduler does. Guards against {@code @Transactional} being bypassed by self-invocation.
 */
@SpringBootTest
@ActiveProfiles("test") //enable test profile
class BatchExpiryJobSchedulingTests {

  @Autowired private BatchExpiryJob job;
  @Autowired private BatchRepository batches;

  private Batch old;
  private Batch fresh;

  @AfterEach
  void tearDown() {
    batches.deleteAllById(java.util.List.of(old.getId(), fresh.getId()));
  }

  @Test
  void runDeletesExpiredBatchesWithoutAnOuterTransaction() {
    Instant now = Instant.now();
    old = batches.save(batch(now.minus(Duration.ofDays(1))));
    fresh = batches.save(batch(now));

    job.run();

    assertFalse(batches.existsById(old.getId()));
    assertTrue(batches.existsById(fresh.getId()));
  }

  private static Batch batch(Instant receivedAt) {
    return new Batch(UUID.randomUUID(), "44:48:c1:a4:2b:60", receivedAt, receivedAt, new int[] {-70});
  }
}
