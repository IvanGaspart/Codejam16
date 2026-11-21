package codejam16.larpscan.service;

import codejam16.larpscan.web.dto.BatchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Fallback {@link BatchIngestionService} used until the JPA-backed implementation is registered.
 */
@Service
@ConditionalOnMissingBean(BatchIngestionService.class)
public class LoggingBatchIngestionService implements BatchIngestionService {

  private static final Logger log = LoggerFactory.getLogger(LoggingBatchIngestionService.class);

  @Override
  public void ingest(BatchRequest batch) {
    log.debug(
        "Accepted batch from scanner={} bssid={} sightings={}",
        batch.scannerId(),
        batch.bssid(),
        batch.sightings().size()
    );
  }
}