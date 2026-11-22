package codejam16.larpscan.service;

import codejam16.larpscan.domain.BusynessSnapshot;
import codejam16.larpscan.repository.BusynessSnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores one {@link BusynessSnapshot} per Floor of Trottier every {@code SNAPSHOT_INTERVAL}
 * (RQ-07). Snapshots hold the unsmoothed percentage, people estimate and coverage from
 * {@link BusynessCalculator}; they feed smoothing and history and are kept forever.
 */
@Component
public class BusynessSnapshotJob {

  private static final Logger log = LoggerFactory.getLogger(BusynessSnapshotJob.class);

  private final BusynessCalculator calculator;
  private final BusynessSnapshotRepository snapshots;
  private final Clock clock;

  public BusynessSnapshotJob(
      BusynessCalculator calculator,
      BusynessSnapshotRepository snapshots,
      Clock clock
  ) {
    this.calculator = calculator;
    this.snapshots = snapshots;
    this.clock = clock;
  }

  /**
   * Scheduler entry point. The first run waits one interval, so startup does no work.
   * {@code @Transactional} must sit here too: calling {@link #snapshot()} on {@code this} bypasses
   * the Spring proxy, so its own annotation would not apply and each Floor would save separately.
   */
  @Transactional
  @Scheduled(
      initialDelayString = "${larp.snapshot-interval-ms}",
      fixedRateString = "${larp.snapshot-interval-ms}"
  )
  public void run() {
    snapshot();
  }

  /**
   * Computes the unsmoothed Busyness of every Trottier Floor at the clock instant and stores it.
   *
   * @return the stored snapshots, one per Floor, ordered by floor number
   */
  @Transactional
  public List<BusynessSnapshot> snapshot() {
    Instant at = clock.instant();
    List<BusynessSnapshot> taken = calculator
        .calculate(JpaBuildingQueryService.TROTTIER_ID, at).stream()
        .map(f -> new BusynessSnapshot(
            f.buildingId(), f.floor(), at, f.busynessPct(), f.people(), f.covered()
        ))
        .toList();
    List<BusynessSnapshot> saved = snapshots.saveAll(taken);
    log.debug("Stored {} busyness snapshots at {}", saved.size(), at);
    return saved;
  }
}
