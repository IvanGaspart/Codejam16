package codejam16.larpscan.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
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
        @Min(1) int smoothingSnapshots,
        @Positive double closeD,
        int closeRssi,
        @Positive double farD,
        int farRssi,
        @Positive @DecimalMax("1") double farDownscaleFactor,
        int apRssiAt1m,
        @Positive double pathLossExponent
        ) {
    /** The far disc must contain the close disc. */
    @AssertTrue(message = "far-d must be greater than close-d")
    public boolean isFarDiscBeyondCloseDisc() {
        return farD > closeD;
    }

    /** The far band {@code [farRssi, closeRssi)} must not be empty. */
    @AssertTrue(message = "close-rssi must be greater than far-rssi")
    public boolean isCloseRssiAboveFarRssi() {
        return closeRssi > farRssi;
    }

    /**
     * CORS settings for public GET endpoints consumed by the Lovable frontend.
     */
    public record Cors(@NotEmpty List<@NotBlank String> allowedOrigins) {
        @ConstructorBinding
        public Cors {
		}
        /**
         * Convenience constructor for single-origin callers and unit tests.
         *
         * @param allowedOrigin single allowed origin pattern
         */
        public Cors(String allowedOrigin) {
            this(allowedOrigin == null ? List.of() : List.of(allowedOrigin));
        }
    }
        }
