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
 * Deletes batches that have left the window (RQ-02). Runs once every {@code WINDOW}, so a batch
 * is kept for at most about two windows.
 *
 * <p>The window is {@code (t − WINDOW, t]}, so a batch received exactly at {@code t − WINDOW} is
 * already outside it and is deleted.
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

  /** Scheduler entry point. The first run waits one window, so startup does no work. */
  @Scheduled(
      initialDelayString = "${larp.window-seconds}",
      fixedRateString = "${larp.window-seconds}",
      timeUnit = TimeUnit.SECONDS
  )
  public void run() {
    expire();
  }

  /**
   * Deletes batches received at or before {@code now − WINDOW}.
   *
   * @return the number of batches deleted
   */
  @Transactional
  public int expire() {
    Instant cutoff = Instant.now(clock).minusSeconds(properties.windowSeconds());
    int deleted = batches.deleteReceivedAtOrBefore(cutoff);
    log.debug("Expired {} batches received at or before {}", deleted, cutoff);
    return deleted;
  }
}
