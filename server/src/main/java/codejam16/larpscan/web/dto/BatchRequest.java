package codejam16.larpscan.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Payload sent by a Scanner to {@code POST /api/batches}.
 *
 * @param scannerId unique UUID generated once per Scanner installation
 * @param bssid lowercase colon-separated BSSID of the Scanner's current Access Point
 * @param sentAt UTC timestamp when the batch was transmitted
 * @param sightings BLE sightings captured during the interval (may be empty)
 */
public record BatchRequest(
    @NotNull(message = "scannerId is required")
    UUID scannerId,

    @NotBlank(message = "bssid is required")
    @Pattern(
        regexp = "^[0-9a-f]{2}(:[0-9a-f]{2}){5}$",
        message = "bssid must be a lowercase colon-separated MAC address"
    )
    String bssid,

    @NotNull(message = "sentAt is required")
    Instant sentAt,

    @NotNull(message = "sightings is required")
    List<@NotNull(message = "sighting entry must not be null") @Valid SightingRequest> sightings
) {
  /** Defensively copies the sightings list while preserving null entries for validation. */
  public BatchRequest {
    if (sightings != null) {
      sightings = Collections.unmodifiableList(new ArrayList<>(sightings));
    }
  }
}