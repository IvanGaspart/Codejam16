package codejam16.larpscan.service;

import codejam16.larpscan.domain.Batch;
import codejam16.larpscan.repository.AccessPointRepository;
import codejam16.larpscan.repository.BatchRepository;
import codejam16.larpscan.web.dto.BatchRequest;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link BatchIngestionService} that stores each batch with its RSSIs, stamped with the server's
 * receive time. Batches with an unknown BSSID are stored and logged (RQ-03).
 */
@Service
public class JpaBatchIngestionService implements BatchIngestionService {

  private static final Logger log = LoggerFactory.getLogger(JpaBatchIngestionService.class);

  private final BatchRepository batches;
  private final AccessPointRepository accessPoints;
  private final Clock clock;

  public JpaBatchIngestionService(
      BatchRepository batches,
      AccessPointRepository accessPoints,
      Clock clock
  ) {
    this.batches = batches;
    this.accessPoints = accessPoints;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void ingest(BatchRequest request) {
    if (!accessPoints.existsById(request.bssid())) {
      log.warn("Batch from scanner={} has unknown bssid={}", request.scannerId(), request.bssid());
    }

    int[] rssis = request.sightings().stream().mapToInt(Integer::intValue).toArray();
    batches.save(new Batch(
        request.scannerId(),
        request.bssid(),
        request.sentAt(),
        Instant.now(clock),
        rssis
    ));
  }
}
