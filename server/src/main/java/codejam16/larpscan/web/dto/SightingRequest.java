package codejam16.larpscan.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

/**
 * Single BLE sighting reported within a scanner batch.
 *
 * @param address lowercase colon-separated BLE MAC address
 * @param rssi received signal strength in dBm
 * @param seenAt UTC timestamp when the sighting was recorded
 */
public record SightingRequest(
    @NotBlank(message = "address is required")
    @Pattern(
        regexp = "^[0-9a-f]{2}(:[0-9a-f]{2}){5}$",
        message = "address must be a lowercase colon-separated MAC address"
    )
    String address,

    @NotNull(message = "rssi is required")
    @Min(value = -127, message = "rssi must be >= -127 dBm")
    @Max(value = 0, message = "rssi must be <= 0 dBm")
    Integer rssi,

    @NotNull(message = "seenAt is required")
    Instant seenAt
) {}