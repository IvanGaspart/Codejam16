package codejam16.larpscan.service;

import codejam16.larpscan.config.LarpProperties;
import codejam16.larpscan.repository.BatchRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prunes batches outside the live occupancy window, retaining the latest per scanner for X-Ray.
 */
@Component
public class BatchExpiryJob {

  private static final Logger log = LoggerFactory.getLogger(BatchExpiryJob.class);

  private final BatchRepository batches;
  private final LarpProperties properties;
  private final Clock clock;

  public BatchExpiryJob(BatchRepository batches, LarpProperties properties, Clock clock) {
    this.batches = batches;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Scheduler entry point. The first run waits one window, so startup does no work.
   * {@code @Transactional} must sit here too: calling {@link #expire()} on {@code this} bypasses
   * the Spring proxy, so its own annotation would not apply.
   */
  @Transactional
  @Scheduled(
      initialDelayString = "${larp.window-seconds}",
      fixedRateString = "${larp.window-seconds}",
      timeUnit = TimeUnit.SECONDS
  )
  public void run() {
    expire();
  }

  /**
   * Deletes batches no longer needed for live occupancy or X-Ray.
   *
   * @return the number of batches deleted
   */
  @Transactional
  public int expire() {
    Instant now = clock.instant();
    Instant cutoff = now.minusSeconds(properties.windowSeconds());
    int deleted = batches.deleteExpired(cutoff, now.minus(BatchRepository.XRAY_LOOKBACK));
    log.debug("Expired {} batches received at or before {}", deleted, cutoff);
    return deleted;
  }
}
