package codejam16.larpscan.service;

import codejam16.larpscan.web.dto.BatchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * * Temporary {@link BatchIngestionService} that logs accepted batches until JPA persistence is wired.
 */
@Service
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
