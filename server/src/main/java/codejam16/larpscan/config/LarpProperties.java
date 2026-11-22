package codejam16.larpscan.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly-typed configuration properties bound from the {@code larp.*} namespace in {@code application.yml}.
 * Centralizes all configurable constants defined in {@code docs/SPEC.md}.
 */
@Validated
@ConfigurationProperties(prefix = "larp")
public record LarpProperties(
		@NotBlank String apiKey,
		@Valid Cors cors,
		@Min(1) int windowSeconds,
		@Positive double k,
		@Min(1000) long snapshotIntervalMs,
		@Min(1) int smoothingSnapshots
) {
	/**
	 * CORS settings for public GET endpoints consumed by the Lovable frontend.
	 */
    public record Cors(@NotEmpty List<@NotBlank String> allowedOrigins) {
	}
}
