package codejam16.larpscan.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
 * @param sightings RSSI values (in dBm) of BLE devices heard during the interval (may be empty)
 * @param aps Wi-Fi Access Points heard nearby, used to locate the Scanner (optional; missing or
 *     {@code null} becomes empty, which leaves the Scanner without a position)
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
    List<
        @NotNull(message = "sighting entry must not be null")
        @Min(value = -127, message = "rssi must be >= -127")
        @Max(value = 0, message = "rssi must be <= 0")
        Integer> sightings,

    @Size(max = 64, message = "aps must have at most 64 entries")
    List<
        @NotNull(message = "aps entry must not be null")
        @Valid
        ApReading> aps
) {
  /**
   * Defensively copies the lists while preserving null entries for validation; a missing
   * {@code aps} becomes empty.
   */
  public BatchRequest {
    if (sightings != null) {
      sightings = Collections.unmodifiableList(new ArrayList<>(sightings));
    }
    aps = aps == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(aps));
  }

  /**
   * One Wi-Fi Access Point heard by the Scanner.
   *
   * @param bssid lowercase colon-separated BSSID of the Access Point
   * @param rssi its signal strength in dBm
   */
  public record ApReading(
      @NotBlank(message = "bssid is required")
      @Pattern(
          regexp = "^[0-9a-f]{2}(:[0-9a-f]{2}){5}$",
          message = "bssid must be a lowercase colon-separated MAC address"
      )
      String bssid,

      @NotNull(message = "rssi is required")
      @Min(value = -127, message = "rssi must be >= -127")
      @Max(value = 0, message = "rssi must be <= 0")
      Integer rssi
  ) {
  }
}
