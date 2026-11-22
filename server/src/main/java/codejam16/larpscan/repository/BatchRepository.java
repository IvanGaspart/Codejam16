package codejam16.larpscan.repository;

import codejam16.larpscan.domain.Batch;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data repository for {@link Batch}.
 */
public interface BatchRepository extends JpaRepository<Batch, Long> {

  Duration XRAY_LOOKBACK = Duration.ofHours(1);

  record LocatedBatch(Batch batch, Integer floor) {}

  @Query("""
      select b, ap.floor from Batch b left join AccessPoint ap on ap.bssid = b.bssid
      where b.receivedAt > :after and b.receivedAt <= :now
        and (ap is null or ap.buildingId = :buildingId)
        and not exists (select 1 from Batch n where n.scannerId = b.scannerId
          and n.receivedAt <= :now and (n.receivedAt > b.receivedAt
            or (n.receivedAt = b.receivedAt and n.id > b.id)))
      order by b.scannerId
      """)
  List<LocatedBatch> latestScannerBatches(String buildingId, Instant after, Instant now);

  /** Outside the live window, keep only each scanner's latest batch for X-Ray. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      delete from Batch b where b.receivedAt <= :liveCutoff and
        (b.receivedAt <= :xrayCutoff or exists (select 1 from Batch n
          where n.scannerId = b.scannerId and (n.receivedAt > b.receivedAt
            or (n.receivedAt = b.receivedAt and n.id > b.id))))
      """)
  int deleteExpired(Instant liveCutoff, Instant xrayCutoff);

  /**
   * Returns every batch received in the half-open interval {@code (after, upTo]}.
   *
   * @param after exclusive lower bound on {@code received_at}
   * @param upTo inclusive upper bound on {@code received_at}
   * @return batches in the interval, in no particular order
   */
  List<Batch> findByReceivedAtGreaterThanAndReceivedAtLessThanEqual(Instant after, Instant upTo);
}
