package codejam16.larpscan.web;

import codejam16.larpscan.service.BatchIngestionService;
import codejam16.larpscan.web.dto.BatchRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for Scanner batch ingestion.
 */
@RestController
@RequestMapping("/api/batches")
public class BatchController {

  private final BatchIngestionService batchIngestionService;

  public BatchController(BatchIngestionService batchIngestionService) {
    this.batchIngestionService = batchIngestionService;
  }

  /**
   * Validates and accepts a batch of BLE sightings from a Scanner.
   *
   * @param request validated batch payload
   * @return {@code 202 Accepted} with an empty body
   */
  @PostMapping
  public ResponseEntity<Void> postBatch(@Valid @RequestBody BatchRequest request) {
    batchIngestionService.ingest(request);
    return ResponseEntity.accepted().build();
  }
}