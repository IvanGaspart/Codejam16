package codejam16.larpscan.service;

import codejam16.larpscan.domain.AccessPoint;
import codejam16.larpscan.domain.Batch;
import codejam16.larpscan.repository.AccessPointRepository;
import codejam16.larpscan.repository.BatchRepository;
import codejam16.larpscan.service.ScannerLocator.Position;
import codejam16.larpscan.web.dto.BatchRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link BatchIngestionService} that stores each batch with its RSSIs, stamped with the server's
 * receive time. Batches with an unknown BSSID are stored and logged (RQ-03).
 *
 * <p>The Scanner's position is trilaterated once here, from the APs it heard on the Floor of its
 * connected BSSID, and stored on the batch; it stays {@code null} for an unknown BSSID or when no
 * heard AP is located on that Floor.
 */
@Service
public class JpaBatchIngestionService implements BatchIngestionService {

  private static final Logger log = LoggerFactory.getLogger(JpaBatchIngestionService.class);

  private final BatchRepository batches;
  private final AccessPointRepository accessPoints;
  private final ScannerLocator locator;
  private final Clock clock;

  public JpaBatchIngestionService(
      BatchRepository batches,
      AccessPointRepository accessPoints,
      ScannerLocator locator,
      Clock clock
  ) {
    this.batches = batches;
    this.accessPoints = accessPoints;
    this.locator = locator;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void ingest(BatchRequest request) {
    Optional<AccessPoint> connected = accessPoints.findById(request.bssid());
    if (connected.isEmpty()) {
      log.warn("Batch from scanner={} has unknown bssid={}", request.scannerId(), request.bssid());
    }
    Position position = connected
        .flatMap(ap -> locator.locate(ap.getFloor(), request.aps()))
        .orElse(null);

    int[] rssis = request.sightings().stream().mapToInt(Integer::intValue).toArray();
    batches.save(new Batch(
        request.scannerId(),
        request.bssid(),
        request.sentAt(),
        Instant.now(clock),
        rssis,
        position == null ? null : position.latitude(),
        position == null ? null : position.longitude()
    ));
  }
}
