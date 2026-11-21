package codejam16.larpscan.service;

import codejam16.larpscan.web.dto.BatchRequest;

/**
 * Persists validated scanner batches and their BLE sightings.
 */
public interface BatchIngestionService {

  /**
   * Ingests a validated batch from a Scanner.
   *
   * @param batch validated batch payload
   */
  void ingest(BatchRequest batch);
}